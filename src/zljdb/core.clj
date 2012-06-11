(ns zljdb.core
  (:refer-clojure :exclude [get get-in remove])
  (:import java.util.concurrent.TimeUnit
           java.util.concurrent.Executors
           java.util.Date))

(defonce ^:dynamic *db* (atom (sorted-map)))
(defonce ^:dynamic *timer* (. Executors newScheduledThreadPool 1))
(defonce ^:dynamic *write-time* (atom (.getTime (Date.))))

(defn zpit
  "Put the content in filename and zip it into zip-filename. Accept
  one or more filename/content. (from 'frozenlock' on IRC)"
  [zip-filename filename content & others]
  (with-open [zip (java.util.zip.ZipOutputStream.
                   (clojure.java.io/output-stream zip-filename))]
    (let [add-entry (fn [name cont remain] 
                      (-> zip (.putNextEntry (java.util.zip.ZipEntry. name)))
                      (doto (java.io.PrintStream. zip true)
                        (.println content))
                      (when (seq remain)
                        (recur (first remain) (second remain) (drop 2 remain))))]
      (add-entry filename content others))))

(defn zlurp 
  "Dumps the contents of all the files in the zip specified into a string."
  [fileName]
  (with-out-str
    (with-open [zip (java.util.zip.ZipFile. fileName)]
      (doseq [e (enumeration-seq (.entries zip))]
        (with-open [eis (.getInputStream zip e)]
          (let [buffer (byte-array 1024)]
            (loop [bytes-read (.read eis buffer 0 1024)]
              (if-not (= bytes-read -1)
                (do (print (String. buffer "UTF-8"))
                  (recur (.read eis buffer 0 1024)))))))))))

(defn get-time [] (.getTime (Date.)))

(defn busy? [] (> 3000 (- (get-time) @*write-time*)))

(defn busy! [] (swap! *write-time* (fn [x] (get-time))))

(defn thread [& expr] (.start (Thread. (fn [] expr))))

(defn dbput! [k v]
  (swap! *db* assoc k v)
  [k v])

(defn dbget [k]
  (clojure.core/get @*db* k))

(defn dbget-in [k ks]
  (clojure.core/get-in (dbget k) ks))

(defn dbremove! [k]
  (swap! *db* dissoc k)
  k)

(defn dbupdate! [k f & args]
  (clojure.core/get 
    (swap! *db* #(assoc % k (apply f (clojure.core/get % k) args))) 
    k))

(defn persist-db []
  (let [cur @*db*]
    (busy!)
    (zpit "./zljdb.zip" "zljdb" (pr-str cur))
    (busy!)))

(defn read-db []
  (let [content (try 
                  (into (sorted-map) (read-string (zlurp "./zljdb.zip")))
                  (catch Exception e
                    (println "zljdb: Could not find" 
                             "a zljdb.zip file. Starting from scratch")
                    (sorted-map)))]
    (reset! *db* content)
    (let [not-empty? (complement empty?)]
      (when (not-empty? content)
        (println "zljdb: " (count content) " keys are loaded.")
        true))))

(defn clear! []
  (reset! *db* (sorted-map))
  (persist-db))

(defn try-persist-twice []
  (if (not (busy?))
    (persist-db)
    (.start
      (Thread.
        (fn []
          (busy!)
          (Thread/sleep 6001)
          (if (not (busy?))
            (persist-db)))))))

(add-watch *db* :persist 
           (fn [_key _ref old-state new-state]
             (try-persist-twice)))

(defn init []
  (read-db)
  (.. Runtime getRuntime (addShutdownHook (Thread. persist-db)))
  (. *timer* (scheduleAtFixedRate persist-db 
                                  (long 60) 
                                  (long 60) 
                                  (. TimeUnit SECONDS))))
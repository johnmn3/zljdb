(defproject zljdb "0.1.0-SNAPSHOT"
  :description "zljdb:  Based on Chris Granger's SimpleDB.  Uses a watcher to
   immediately persist (with some contention back off), in addition to a persist
   once every 60 seconds.  Also stores the db in a zip file.  The name
   zljdb is from: Zipped cLJ DataBase."
  :dependencies [[org.clojure/clojure "1.3.0"]]
  :main zljdb.core)
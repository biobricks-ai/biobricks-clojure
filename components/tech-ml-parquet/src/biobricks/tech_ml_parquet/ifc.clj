(ns biobricks.tech-ml-parquet.ifc
  (:require [clojure.data.json :as json])
  (:import [org.apache.parquet.hadoop ParquetFileReader]
           [org.apache.parquet.hadoop.metadata ParquetMetadata]
           [org.apache.hadoop.conf Configuration]
           [org.apache.hadoop.fs Path FileSystem]))

(defn read-metadata
  [file-path]
  (let [conf (Configuration.)
        fs (FileSystem/get conf)
        path (Path. file-path)]
    (-> (with-open [reader (ParquetFileReader/open (.getConf fs) path)]
          (.getFooter reader))
      ParquetMetadata/toJSON
      (json/read-str {:key-fn keyword}))))

(comment
  (->
    (read-metadata
      "/home/john/src/biobricks-clojure/bricks/biobricks-ai/tox21/brick/tox21_aggregated.parquet")
    :fileMetaData
    :schema
    :columns
    #_keys))

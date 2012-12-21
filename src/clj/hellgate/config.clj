(ns hellgate.config
  (:require [clojure.java.io :as io])
  (:import [java.io PushbackReader]))

(def config (binding [*read-eval* false]
              (with-open [r (io/reader "resources/config.clj")]
                (read (PushbackReader. r)))))

(defn get-remote-service-uri
  [amf-uri]
  (let [remote-resource (get-in config [:services amf-uri])]
    (when (nil? remote-resource)
      (throw (Exception. (format "no_remote_resource_found, args=[%s]" amf-uri))))
    remote-resource))

(ns hellgate.generator
  (:use camel-snake-kebab)
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clostache.parser :as parser])
  (:import [java.io PushbackReader]))

;; FIXME currently depends on existing "tmp" directory
;; TODO generate directory structure for packages
(defn generate-bean
  [definition]
  (let [split-path (clojure.string/split (:class-name definition) #"\.")
        simple-name (last split-path)
        package (clojure.string/join "." (butlast split-path))
        data {:package package
              :simple-name simple-name
              :fields (for [[k v] (seq (:fields definition))]
                        {:name (->camelCase (name k)) :type v})
              :getter-setter (for [[k v] (seq (:fields definition))]
                               (let [field-name (->camelCase (name k))]
                                 {:name field-name
                                  :type v
                                  :getter (->camelCase (str "get-" (name k)))
                                  :setter (->camelCase (str "set-" (name k)))}))}]
    (with-open [w (io/writer (str "tmp/" simple-name ".java"))]
      (.write w (parser/render-resource "templates/bean.mustache" data)))))

(defn from-definitions-file
  [filename]
  (let [definitions (binding [*read-eval* false]
                      (with-open [r (io/reader filename)]
                        (read (PushbackReader. r))))]
    (doseq [definition definitions]
      (generate-bean definition))))

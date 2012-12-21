(ns hellgate.json
  (:require [clojure.tools.logging :as log]
            [hellgate.config :as c])
  (:import com.google.gson.stream.JsonToken
           [com.google.gson Gson GsonBuilder JsonSerializer JsonDeserializer JsonObject
            JsonPrimitive JsonElement JsonSerializationContext JsonDeserializationContext
            TypeAdapterFactory TypeAdapter]))

(def alias-tag "json_class")
(def data-tag "data")

(def forward-aliases (atom {}))
(def forward-class-lookup (atom #{}))

(defn register-forward-alias
  [alias class-string]
  (let [clazz (Class/forName class-string)]
    (swap! forward-aliases assoc alias clazz)
    (swap! forward-class-lookup conj clazz)))

(defn register-forward-aliases
  [alias-config]
  (doseq [[class-string alias] alias-config]
    (register-forward-alias alias class-string)))

(register-forward-aliases (:remote-classes c/config))

(println "forward-aliases" @forward-aliases)
(println "forward-class-lookup" @forward-class-lookup)

(defn handle-primitive
  [json]
  (cond
   (.isBoolean json) (.getAsBoolean json)
   (.isString json) (.getAsString json)
   :else (.getAsFloat json)))

(defn handle-array
  [json context]
  (to-array (for [item json]
              (.deserialize context item java.util.HashMap))))

(defn handle-object
  [json context]
  (if (.has json alias-tag)
    (let [alias (.getAsString (.get json alias-tag))
          data (.get json data-tag)]
      (.deserialize context data (get @forward-aliases alias)))
    (let [map (java.util.HashMap.)]
      (doseq [entry (.entrySet json)]
        (.put map (.getKey entry) (.deserialize context (.getValue entry) java.util.HashMap)))
      map)))

(defn get-custom-deserializer
  []
  (reify JsonDeserializer
    (deserialize [this json type context]
      (cond
       (.isJsonNull json) nil
       (.isJsonPrimitive json) (handle-primitive (.getAsJsonPrimitive json))
       (.isJsonArray json) (handle-array (.getAsJsonArray json) context)
       :else (handle-object (.getAsJsonObject json) context)))))

(defn get-custom-type-adapter
  [delegate element-adapter]
  (proxy [TypeAdapter] []
    (write [out value]
      (if (nil? value)
        (.nullValue out)
        (let [tree (.toJsonTree delegate value)
              json-clone (JsonObject.)]
          (.add json-clone alias-tag (JsonPrimitive. (.getSimpleName (class value))))
          (.add json-clone data-tag tree)
          (.write element-adapter out json-clone))))
    (read [reader]
      (if (= (.peek reader) (JsonToken/NULL))
        (do
          (.nextNull reader)
          nil)
        (.read delegate reader)))))

(defn get-type-adapter-factory
  []
  (reify TypeAdapterFactory
    (create [this gson type]
      (if (contains? @forward-class-lookup (.getRawType type))
        (let [delegate (.getDelegateAdapter gson this type)
              element-adapter (.getAdapter gson JsonElement)]
          (get-custom-type-adapter delegate element-adapter))
        nil))))

(def gson (doto (GsonBuilder.)
            (.registerTypeAdapter java.util.HashMap (get-custom-deserializer))
            (.registerTypeAdapterFactory (get-type-adapter-factory))))

(defn serialize
  [content]
  (.toJson (.create gson) content))

(defn deserialize
  [json]
  (.get (.fromJson (.create gson) json java.util.HashMap) "content"))

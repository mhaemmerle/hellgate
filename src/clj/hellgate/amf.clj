(ns hellgate.amf
  (:use [aleph http formats])
  (:require [clojure.tools.logging :as log]
            [hellgate.json :as j])
  (:import [flex.messaging.io SerializationContext MessageIOConstants
            ClassAliasRegistry]
           [flex.messaging.io.amf ASObject Amf3Input Amf3Output ActionMessage
            MessageBody AmfMessageSerializer ActionContext AmfMessageDeserializer
            AmfTrace]
           [flex.messaging.io.amf.client AMFConnection]
           [flex.messaging.io.amf.client.exceptions ClientStatusException
            ServerStatusException]
           [flex.messaging.messages CommandMessage AcknowledgeMessage RemotingMessage]
           [java.io ByteArrayOutputStream ObjectOutputStream FileOutputStream
            File BufferedInputStream BufferedOutputStream]))

;; action-message
;;   headers (empty)
;;   message-body
;;     target -> getTargetURI
;;     response (counter)
;;     content (could be array)
;; action-message methods: getHeader, getHeaders, getBodyCount, getBodies
;; message-body methods: getTargetURI, getData (getResponseURI, getReplyMethod)

(defn register-alias
  [^String alias ^String class-name]
  (let [^ClassAliasRegistry registry (ClassAliasRegistry/getRegistry)]
    (.registerAlias registry alias class-name)))

(def amf-default-headers
  {"Content-Type" "application/x-amf"
   "Content-Transfer-Encoding" "binary"
   "Content-Disposition" "inline"})

(defn get-serialization-context
  []
  (doto (SerializationContext/getSerializationContext)
    (-> .enableSmallMessages (set! true))
    (-> .instantiateTypes (set! true))
    (-> .supportRemoteClass (set! true))
    (-> .legacyCollection (set! false))
    (-> .legacyMap (set! false))
    (-> .legacyXMLDocument (set! false))
    (-> .legacyXMLNamespaces (set! false))
    (-> .legacyThrowable (set! false))
    (-> .legacyBigNumbers (set! false))
    (-> .restoreReferences (set! false))
    (-> .logPropertyErrors (set! false))
    (-> .ignorePropertyErrors (set! true))))

(defn debug-to-file
  [input-stream]
  (let [out (BufferedOutputStream. (FileOutputStream. "debug.amf"))
        buffer (make-array Byte/TYPE 1024)]
    (loop [g (.read input-stream buffer)
           r 0]
      (if-not (= g -1)
        (do
          (.write out buffer 0 g)
          (recur (.read input-stream buffer) (+ r g)))))
    (.close out)))

(defn serialize
  [serialization-context action-message]
  (let [amf-trace (AmfTrace.)
        out-buffer (ByteArrayOutputStream.)]
    (doto (AmfMessageSerializer.)
      (.initialize serialization-context out-buffer amf-trace)
      (.writeMessage action-message))
    (.toByteArray out-buffer)))

(defn deserialize
  [input-stream]
  (let [buffered-input-stream (BufferedInputStream. input-stream)
        ;; _ (debug-to-file buffered-input-stream)
        action-context (ActionContext.)
        serialization-context (get-serialization-context)
        action-message (ActionMessage.)
        deserializer (AmfMessageDeserializer.)
        amf-trace (AmfTrace.)]
    (.setRequestMessage action-context action-message)
    (doto deserializer
      (.initialize serialization-context buffered-input-stream amf-trace)
      (.readMessage action-message action-context))
    action-message))

;; TODO actually get action-context in here?
(defn get-response-context
  []
  {:action-message (ActionMessage. 3)
   :serialization-context (get-serialization-context)})

(defn add-response-message!
  [response-context ^flex.messaging.messages.Message request-message data]
  (let [response-counter 0
        target-uri (str "/" response-counter "/onResult")
        response-uri nil
        response-message (doto (AcknowledgeMessage.)
                           (.setBody data)
                           (.setCorrelationId (.getMessageId request-message))
                           (.setTimestamp 0)
                           (.setTimeToLive 0))
        message-body (MessageBody. target-uri response-uri response-message)]
    (.addBody (:action-message response-context) message-body))
  response-context)

(defn build-simple-action-message
  [json]
  (let [response-message (j/deserialize json)
        ;; action-context (ActionContext.)
        serialization-context (get-serialization-context)
        response-counter 0
        response-uri nil
        action-message (ActionMessage. 3)
        command (str "/" response-counter "/onResult")
        message-body (MessageBody. command response-uri response-message)]
    (.addBody action-message message-body)
    ;; (.setRequestMessage action-context request-message)
    (serialize serialization-context action-message)))

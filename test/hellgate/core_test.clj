(ns hellgate.core-test
  (:use clojure.test
        hellgate.core
        [lamina core executor]
        [aleph http formats]
        [cheshire.core :only [generate-string parse-string]])
  (:require [clojure.tools.logging :as log]
            [hellgate.amf :as amf]
            [hellgate.json :as j])
  (:import flex.messaging.io.amf.client.AMFConnection
           flex.messaging.io.ClassAliasRegistry
           [com.hellgate.client.model ClientFoo ClientBar ClientQux]
           com.google.gson.Gson))

(def endpoint-url "http://localhost:8080/amf")

(def forward-alias-config
  [["com.hellgate.client.model.ClientFoo" "ClientFoo"]
   ["com.hellgate.client.model.ClientBar" "ClientBar"]
   ["com.hellgate.client.model.ClientQux" "ClientQux"]])

(defn do-amf-call
  [command arguments]
  (let [^AMFConnection amf-connection (AMFConnection.)]
    (amf/register-alias "com.hellgate.client.model.ClientFoo" "com.hellgate.model.Foo")
    (.connect amf-connection endpoint-url)
    (.call amf-connection command (to-array arguments))))

(defn start-mock-remote-service
  [handler port]
  (start-http-server (wrap-ring-handler handler) {:port port}))

(deftest receive-simple-message
  (start-server)
  (j/register-forward-aliases forward-alias-config)
  (let [id 908
        message "Hell!"
        mock-response-body (generate-string {:content
                                             {:json_class "ClientQux"
                                              :data {:id id :message message}}})
        mock-remote-service (start-mock-remote-service
                             (fn [request]
                               {:status 200
                                :headers {"content-type" "application/json"}
                                :body mock-response-body}) 3000)
        message (doto (ClientFoo.)
                  (.setId 123)
                  (.setUserId 456)
                  (.setBar (doto (ClientBar.)
                             (.setId 789))))]
    (let [result (do-amf-call "FooBarController.baz" [message])]
      (log/info "result" result)
      (is (.getId result) id)
      (is (.getMessage result) message)))
  (stop-server))

(deftest let-configured-messages-pass-through
  (let [pass-through-get-or-create "UsersController.get_or_create_user"]
    (register-for-pass-through pass-through-get-or-create)

    ))

(deftest receive-remoting-message
  (testing "receive remoting blub"
    (is (= 1 1)))
  )

(deftest build-batched-request
  (j/register-forward-aliases forward-alias-config)
  (let [response-context (amf/get-response-context)
        message-data (doto (ClientFoo.)
                       (.setId 123)
                       (.setUserId 456)
                       (.setBar (doto (ClientBar.)
                                  (.setId 789))))
        request-message (proxy [flex.messaging.messages.RemotingMessage] []
                          (getMessageId [] "123"))
        request-message-body (proxy [flex.messaging.io.amf.MessageBody] []
                               (getResponseURI [] "/0")
                               (getData [] request-message))]
    (dotimes [n 3]
      (amf/add-response-message! response-context request-message-body message-data))
    (let [byte-array (amf/serialize (:serialization-context response-context)
                                    (:action-message response-context))]
      (with-open [w (clojure.java.io/output-stream "debug.amf")]
        (.write w byte-array)))))

(deftest build-acknowledge-message
  (let [response-context (amf/get-response-context)
        message-body (doto (ClientFoo.)
                       (.setId 123)
                       (.setUserId 456)
                       (.setBar (doto (ClientBar.)
                                  (.setId 789))))
        request-message (proxy [flex.messaging.messages.RemotingMessage] []
                          (getMessageId [] "123"))
        request-message-body (proxy [flex.messaging.io.amf.MessageBody] []
                               (getResponseURI [] "/0")
                               (getData [] request-message))]
    (amf/add-response-message! response-context request-message-body message-body)
    ;; (let [byte-array (amf/serialize (:serialization-context response-context)
    ;;                                 (:action-message response-context))]
    ;;   (with-open [w (clojure.java.io/output-stream "debug.amf")]
    ;;   (.write w byte-array)))
    ))

;; (def t "{\"content\":{\"json_class\":\"Qux\",\"data\":{\"id\":0,\"message\":\"msg\"}}}")
;; (defn deserialize-json
;;   [json]
;;   (.fromJson (.create gson) json java.util.HashMap))
;; (log/info "t" (deserialize-json t))

;; (dotimes [n 30]
;;   (.put ^java.util.HashMap d (str "prop" n) n))
;; (let [g (.create gson)]
;;   (dotimes [n 1000]
;;     (time (dotimes [n 5000]
;;             (let [;; d (doto ^java.util.HashMap (java.util.HashMap.)
;;                   ;;     (.put "propx" "value1")
;;                   ;;     (.put "propy" (to-array [(create-qux) (create-qux)]))
;;                   ;;     (.put "baz" (create-baz)))
;;                   ;; _ (dotimes [n 10]
;;                   ;;     (.put ^java.util.HashMap d (str "prop" n) n))
;;                   serialized (.toJson g d)]
;;               (.fromJson g serialized java.util.HashMap))))
;;     (Thread/sleep 500)))
;; (defn register-object-mapping
;;   [name clazz]
;;   (swap! object-mappings assoc name clazz))

;; (defn create-qux
;;   []
;;   (doto (Qux.)
;;     (.setId 0)
;;     (.setMessage "msg")))
;; (defn create-baz
;;   []
;;   (doto (Baz.)
;;     (.setId 0)
;;     (.setQux (create-qux))))
;; (log/info "qux" (.toJson (.create gson) (create-qux)))
;; (log/info "baz" (.toJson (.create gson) (create-baz)))
;; (let [g (.create gson)
;;       d (doto (java.util.HashMap.)
;;           (.put "prop1" "value1")
;;           (.put "prop2" (to-array [(create-qux) (create-qux)]))
;;           (.put "baz" (create-baz)))
;;       serialized (.toJson g d)
;;       deserialized (.fromJson g serialized java.util.HashMap)]
;;   (log/info "m#1" serialized)
;;   (log/info "m#2" deserialized)
;;   (log/info "m#3" (.toJson g deserialized)))
;; (def d (doto (java.util.HashMap.)
;;          (.put "prop1" "value1")
;;          (.put "prop2" (to-array [(create-qux) (create-qux)]))
;;          (.put "baz" (create-baz))))

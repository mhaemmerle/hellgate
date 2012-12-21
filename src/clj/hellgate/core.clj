;; - move most of core ns into gateway ns
;; - more front-to-back tests
;; - error handling in general should be better
;; - more idiomatic handling of state "(with-..."

(ns hellgate.core
  (:gen-class)
  (:use clojure.tools.cli
        camel-snake-kebab
        [lamina core executor]
        [aleph http formats]
        [cheshire.core :only [generate-string parse-string]]
        compojure.core)
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [compojure.route :as route]
            [hellgate.json :as j]
            [hellgate.amf :as amf]
            [hellgate.config :as config])
  (:import [flex.messaging.messages AcknowledgeMessage CommandMessage RemotingMessage]
           [flex.messaging.io.amf ActionMessage MessageBody]
           flex.messaging.io.amf.client.AMFConnection
           [flex.messaging.io.amf.client.exceptions ClientStatusException
            ServerStatusException]
           [java.io ByteArrayOutputStream ObjectOutputStream FileOutputStream
            File BufferedInputStream]))

(set! *warn-on-reflection* true)

(defn print-logo [file-name]
  (with-open [r (io/reader file-name)]
    (doseq [line (line-seq r)]
      (println line))))

(def amf-default-port 8080)

(def remote-default-port 3000)
(def remote-default-host "http://localhost")

(def aleph-stop (atom nil))

(def build-service-url (memoize
                        (fn [host port resource]
                          (str host ":" port "/" resource))))

(defn- wrap-bounce-favicon [handler]
  (fn [req]
    (if (= [:get "/favicon.ico"] [(:request-method req) (:uri req)])
      {:status 404
       :headers {}
       :body ""}
      (handler req))))

;; target LandingPageController.manifest_path
;; response /..
;; content -> array
;; or
;; target == nil
;; response == /..
;; content -> flex.messaging.messages.RemotingMessage
;; operation, source, body -> array
;; or
;; target == nil
;; content -> flex.messaging.messages.CommandMessage

(defn handle-simple-message
  "Handles simple AMF messages that consist of a single message body and are of
   no specific type."
  [request-channel ^MessageBody message-body]
  (log/info "handle-simple-message")
  (let [target-uri (.getTargetURI message-body)
        remote-uri (config/get-remote-service-uri target-uri)
        ;; TODO get host and port from config/bindings
        service-url (build-service-url remote-default-host
                                       remote-default-port
                                       remote-uri)
        content (.getData message-body)
        serialized-content (j/serialize content)
        request {:method :post, :url service-url :body serialized-content}]
    
    (run-pipeline
     request
     {:error-handler (fn [error] (log/info "pipeline_error" error))}
     #(http-request %)
     (fn [remote-response]
       (let [json (bytes->string (:body remote-response))
             response {:status 200
                       :headers amf/amf-default-headers
                       :body (amf/build-simple-action-message json)}]
         (enqueue request-channel response))))))

(defn handle-command-message
  [response-context ^CommandMessage message]
  (amf/add-response-message! response-context message nil))

(defn handle-acknowledge-message
  [response-context ^AcknowledgeMessage message]
  (amf/add-response-message! response-context message nil))

(defn handle-remoting-message
  [response-context ^RemotingMessage message]
  (let [source (.getSource message)
        operation (.getOperation message)
        remote-uri (config/get-remote-service-uri (str source "." operation))
        ;; TODO get host and port from config/bindings
        service-url (build-service-url remote-default-host
                                       remote-default-port
                                       remote-uri)
        serialized-body (j/serialize (.getBody message))
        request {:method :post, :url service-url :body serialized-body}]

    (run-pipeline
     request
     {:error-handler (fn [error] (log/info "remoting_pipeline_error" error))}
     #(http-request %)
     #(amf/add-response-message! response-context message %))))

(defn handle-complex-messages
  "Handles typed AMF messages that need responses according to the FLEX messaging
   implementation. It is possible that there is more than just message body."
  [request-channel message-bodies]
  (log/info "handle-complex-message")
  (run-pipeline
   (amf/get-response-context)
   {:error-handler (fn [error] (log/info "pipeline_error" error))}
   (fn [response-context]
     (task
      (doseq [^MessageBody message-body message-bodies]
        (let [message (.getData message-body)]
          (case (type message)
            #=flex.messaging.messages.CommandMessage
            (handle-command-message response-context message)
            #=flex.messaging.messages.AcknowledgeMessage
            (handle-acknowledge-message response-context message)
            #=flex.messaging.messages.RemotingMessage
            (handle-remoting-message response-context message)
            (throw (Exception. "got_unexpected_amf_request")))))
      response-context))
   (fn [response-context]
     (let [response (amf/serialize (:serialization-context response-context)
                                   (:action-message response-context))]
       (enqueue request-channel {:status 200
                                 :headers amf/amf-default-headers
                                 :body response})))))

(defn amf-handler
  [request-channel request]
   (let [^ActionMessage request-message (amf/deserialize (:body request))
         message-bodies (.getBodies request-message)
         ^MessageBody message-body (first message-bodies)]
     (if (not (nil? (.getTargetURI message-body)))
       (handle-simple-message request-channel message-body)
       (handle-complex-messages request-channel message-bodies))))

(def handlers
  (routes
   (POST "/amf" [] (wrap-aleph-handler amf-handler))
   (route/resources "/")
   (route/not-found "Page not found")))

(def app
  (-> handlers
      wrap-bounce-favicon))

(defn start-server
  ([]
     (start-server amf-default-port))
  ([port]
     (let [p (or port (amf-default-port))
           wrapped-handler (wrap-ring-handler app)
           stop-fn (start-http-server wrapped-handler {:port p})]
       (println (format "Starting server on port %d..." p))
       (reset! aleph-stop stop-fn)
       stop-fn)))

(defn stop-server
  []
  (@aleph-stop))

(defn- at-exit
  [runnable]
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable runnable)))

(defn -main
  [& args]
  (print-logo "logo")
  (start-server)
  (println "We're up and running!")
  (at-exit stop-server))

;; (handle-complex-messages
;;  (channel)
;;  (do
;;    ;; (amf/register-forward-aliases forward-alias-config)
;;    (let [response-context (amf/get-response-context)
;;          message-body (doto (com.hellgate.model.Qux.)
;;                         (.setId 0)
;;                         (.setMessage "msg"))
;;          request-message (proxy [flex.messaging.messages.RemotingMessage] []
;;                            (getMessageId [] "123"))]
;;      (dotimes [n 3]
;;        (amf/add-response-message! response-context request-message message-body))
;;      (.getBodies (:action-message response-context)))))

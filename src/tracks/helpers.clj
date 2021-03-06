(ns tracks.helpers
  (:require
    [tracks.models :as m]
    [buddy.hashers :as hashers]
    [selmer.parser :refer [render-file cache-off! cache-on!]]
    ))

(cache-off!)
;(cache-on!)

(defn login-user [login password]
  (let [user-from-db (m/get-username login)]
    (if (= 1 (:activated user-from-db))
      (if (hashers/check password (:password user-from-db))
        user-from-db
        nil)
      nil)
    )
  )

(defn is-authenticated? [req]
  (if (get-in req [:session :identity :login] nil)
    true
    false))

(def counter (atom {}))
(def global-counter (atom 0))

(defn counter-update [uri]
  (swap! global-counter inc)
  (get (swap! counter assoc uri (inc (get @counter uri 0))) uri)
  )

(defn file-renderer [req filename context-map & xs]
  ;(render-file filename context-map xs)
  (render-file filename (-> context-map
                            (assoc :req req)
                            (assoc :sess (:session req))
                            (assoc :counter (:counter req))
                            ) xs)
  )

(def logger (agent (clojure.java.io/writer "log" :append true)))
(def flush-every 4)                                         ; requests

(defn write-callback [writer msg]
  (.write writer msg)
  writer)

(defn flush-callback [writer]
  (.flush writer)
  writer)

(defn logger-flush []
  (send logger flush-callback))

(defn log-request [request-str]
  (send logger write-callback (str request-str "\n"))

  (when (= 0 (mod @global-counter flush-every))
    (logger-flush))
  )

;DSL macros
(defn join-str
  [sep coll]
  (apply str (interpose sep coll)))

(defn content-tag
  ([tag]
    (content-tag tag {} ""))
  ([tag attrs & contents]
    (if-not (map? attrs)
            (apply content-tag tag {} (cons attrs contents))
            (let [tagname (name tag)
                  attribute-strings (map (fn [[k v]] (str (name k) "=\"" (name v) "\"")) attrs)
                  attribute-string (join-str " " attribute-strings)
                  flatten-content (fn [c] (if (seq? c) (apply content-tag c) c))
                  body (join-str " " (map flatten-content contents))]
              (format "<%s %s>%s</%s>" tagname attribute-string body tagname)))))


(defmacro markup
  [& elements]
  `(apply str (map (partial apply content-tag) '~elements)))
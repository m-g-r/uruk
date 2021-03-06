(ns uruk.core-test
  (:require [clojure.test :refer :all]
            [uruk.core :refer :all])
  (:import [java.util.logging Logger]
           [java.util Locale TimeZone]
           [com.marklogic.xcc RequestOptions ContentSource]))

;; FIXME You'll have to fill in database credentials that work for
;; your system:
(def db {:uri "xdbc://localhost:8383/"
         :user "uruk-tester" :password "password"
         :content-base "UrukDB"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Request options
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(println "Testing with MarkLogic XCC version" (:string version))

(deftest default-request-options
  (testing "Creating a request with no explicitly-set options must reflect default options"
    (is (= (request-options->map (make-request-options {}))
           {:timezone nil,
            :cache-result true,
            :locale nil,
            :request-time-limit -1,
            :default-xquery-version nil,
            :timeout-millis -1,
            :query-language nil,
            :result-buffer-size 0,
            :effective-point-in-time nil,
            :request-name nil,
            :auto-retry-delay-millis -1,
            :max-auto-retry -1}))))

(deftest create-session-with-parms
  (testing "Creating sessions with a variety of parameters"
    (testing "...with just URI"
      (is (thrown? IllegalStateException
                   (with-open [session (create-session {:uri "xdbc://localhost:8383/"})]
                     (.submitRequest session (.newAdhocQuery session "\"Hello world\""))))))

    (testing "...with URI and content-base"
      (is (thrown? IllegalStateException
                   (with-open [session (create-session {:uri "xdbc://localhost:8383/"
                                                        :content-base "UrukDB"})]
                     (.submitRequest session (.newAdhocQuery session "\"Hello world\""))))))

    (testing "...with URI, user, password"
      (is (= "Hello world"
             (with-open [session (create-session {:uri "xdbc://localhost:8383/"
                                                  :user "uruk-tester" :password "password"})]
               (-> session
                   (.submitRequest (.newAdhocQuery session
                                                   "\"Hello world\""))
                   .asString)))))

    (testing "...with URI, user, password, content-base"
      (is (= "Hello world"
             (with-open [session (create-session db)]
               (-> session
                   (.submitRequest (.newAdhocQuery session
                                                   "\"Hello world\""))
                   .asString)))))))

(deftest sample-request-options
  (testing "Set sample option on request"
    (is (= 6000
           (.getTimeoutMillis (make-request-options {:timeout-millis 6000}))))))

(deftest roundtrip-request-options
  (testing "All request options must be set as indicated"
    (is (= (let [req-opts (make-request-options {:timezone (TimeZone/getTimeZone "Pacific/Chuuk")
                                                 :cache-result false,
                                                 :locale (Locale. "ru"),
                                                 :request-time-limit 156,
                                                 :default-xquery-version "xquery version \"0.9-ml\";",
                                                 :timeout-millis 763,
                                                 :query-language "Elvish"
                                                 :result-buffer-size 23,
                                                 :effective-point-in-time 14701453805890320
                                                 :request-name "JigoroKano",
                                                 :auto-retry-delay-millis 991,
                                                 :max-auto-retry 3})]
             (request-options->map req-opts))
           {:timezone (TimeZone/getTimeZone "Pacific/Chuuk")
            :cache-result false,
            :locale (Locale. "ru"),
            :request-time-limit 156,
            :default-xquery-version "xquery version \"0.9-ml\";",
            :timeout-millis 763,
            :query-language "Elvish"
            :result-buffer-size 23,
            :effective-point-in-time 14701453805890320,
            :request-name "JigoroKano",
            :auto-retry-delay-millis 991,
            :max-auto-retry 3}))))

(deftest fail-on-invalid-request-option
  (testing "Options that don't exist must raise an error"
    (is (thrown? java.lang.IllegalArgumentException
                 (with-open [sess (create-session db)]
                   (execute-xquery sess "\"hello world\"" {:options {:reuqest-time-limt 500}}))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Session configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn is-default-session-config?
  "True if given session configuration conforms to the expected
  default Session; false otherwise."
  [session]
  (and (is (instance? RequestOptions (:default-request-options session)))
       (is (instance? RequestOptions (:effective-request-options session)))
       (is (and (instance? Logger (:logger session))
                (= "com.marklogic.xcc" (.getName (:logger session)))))
       (is (nil? (:user-object session)))
       (is (= 0 (:transaction-timeout session)))
       (is (nil? (:transaction-mode session)))))

(deftest default-session-config
  (testing "A session with no explicitly-set configuration must have default configuration"
    (testing "basic session creation with full database specified"
      (with-open [sess (create-session db)]
        (is-default-session-config? (session->map sess))))
    (testing "session creation with URI content source"
      (with-open [sess (create-session
                        db (make-uri-content-source "xdbc://localhost:8383/") {})]
        (is-default-session-config? (session->map sess))))
    (testing "session creation with URI content source using options"
      (with-open [sess (create-session
                        db (make-uri-content-source
                            "xdbc://localhost:8383/"
                            {:preemptive-auth false}) {})]
        (is-default-session-config? (session->map sess))))
    (testing "session creation with hosted content source"
      (with-open [sess (create-session
                        db (make-hosted-content-source "localhost" 8383) {})]
        (is-default-session-config? (session->map sess))))
    (testing "session creation with hosted content source using options"
      (with-open [sess (create-session
                        db (make-hosted-content-source "localhost" 8383
                                                       {:content-base "UrukDB"})
                        {})]
        (is-default-session-config? (session->map sess))))
    ;; TODO once we want to delve into extreme complexity of ConnectionProvider
    ;; (testing "session creation with connectionProvider content source"
    ;;   (with-open [sess (create-session
    ;;                     db (make-cp-content-source ...)
    ;;                     {})]
    ;;     (is-default-session-config? (session->map sess))))
    ;; TODO once we want to delve into extreme complexity of ConnectionProvider
    ;; (testing "session creation with connectionProvider content source with options"
    ;;   (with-open [sess (create-session
    ;;                     db (make-cp-content-source ...) {})])
    ;;   (is-default-session-config? (session->map sess)))
    ))

;; (deftest accept-only-valid-session-options
;;   (testing "Invalid session config options should throw an error"
;;     (let [sess-opts (session-options
;;                      (create-session db
;;                                      {:this-does-not-exist "irrelevant string"}))])))


(defn as-expected-session-config?
  "True if given `session` is configured as according to
  `expected-config`; false otherwise."
  [session expected-config]
  (and (and (= (:auto-retry-delay-millis (:default-request-options expected-config))
               (.getAutoRetryDelayMillis (:default-request-options session))
               (.getAutoRetryDelayMillis (:effective-request-options session)))
            (= (:cache-result (:default-request-options expected-config))
               (.getCacheResult (:default-request-options session))
               (.getCacheResult (:effective-request-options session)))
            (= (:default-xquery-version (:default-request-options expected-config))
               (.getDefaultXQueryVersion (:default-request-options session))
               (.getDefaultXQueryVersion (:effective-request-options session)))
            (= (:effective-point-in-time (:default-request-options expected-config))
               (.getEffectivePointInTime (:default-request-options session))
               (.getEffectivePointInTime (:effective-request-options session)))
            (= (:locale (:default-request-options expected-config))
               (.getLocale (:default-request-options session))
               (.getLocale (:effective-request-options session)))
            (= (:max-auto-retry (:default-request-options expected-config))
               (.getMaxAutoRetry (:default-request-options session))
               (.getMaxAutoRetry (:effective-request-options session)))

            (= (:query-language (:default-request-options expected-config))
               (.getQueryLanguage (:default-request-options session))
               (.getQueryLanguage (:effective-request-options session)))
            (= (:request-name (:default-request-options expected-config))
               (.getRequestName (:default-request-options session))
               (.getRequestName (:effective-request-options session)))
            (= (:request-time-limit (:default-request-options expected-config))
               (.getRequestTimeLimit (:default-request-options session))
               (.getRequestTimeLimit (:effective-request-options session)))
            (= (:result-buffer-size (:default-request-options expected-config))
               (.getResultBufferSize (:default-request-options session))
               (.getResultBufferSize (:effective-request-options session)))
            (= (:timeout-millis (:default-request-options expected-config))
               (.getTimeoutMillis (:default-request-options session))
               (.getTimeoutMillis (:effective-request-options session)))
            (= (:timezone (:default-request-options expected-config))
               (.getTimeZone (:default-request-options session))
               (.getTimeZone (:effective-request-options session))))

       (and (instance? Logger (:logger session))
            (= (.getName (:logger expected-config))
               (.getName (:logger session))))
       (= (:user-object expected-config)
          (:user-object session))
       (= (:transaction-timeout session)
          (:transaction-timeout expected-config))
       (= (:transaction-mode session)
          (:transaction-mode expected-config))))

(deftest set-session-config
  (testing "A session with explicitly-set configuration must reflect that configuration"
    (let [dummy-session (create-session db)
          opts {:default-request-options {:auto-retry-delay-millis 98
                                          :cache-result false
                                          :default-xquery-version "0.9-ml"
                                          ;; XXX requires `xdmp:timestamp` execute privilege on the role for the current user
                                          :effective-point-in-time (.getCurrentServerPointInTime
                                                                    dummy-session)
                                          :locale (Locale. "ru")
                                          :max-auto-retry 17
                                          :query-language "de"
                                          :request-name "Phil"
                                          :request-time-limit 8781
                                          :result-buffer-size 3945
                                          :timeout-millis 75
                                          :timezone (TimeZone/getTimeZone "Pacific/Chuuk")}
                :logger (Logger/getLogger "test")
                :transaction-timeout 56
                :transaction-mode :query}]

      (testing "with standard database map"
        (with-open [sess (create-session db opts)]
          (as-expected-session-config? (session->map sess) opts)))
      (testing "with uri content source"
        (with-open [sess (create-session
                          db (make-uri-content-source "xdbc://localhost:8383/")
                          opts)]
          (as-expected-session-config? (session->map sess) opts)))
      (testing "with uri content source using options"
        (with-open [sess (create-session
                          db (make-uri-content-source "xdbc://localhost:8383/"
                                                      {:preemptive-auth false})
                          opts)]
          (as-expected-session-config? (session->map sess) opts)))

      (testing "with hosted content source"
        (with-open [sess (create-session
                          db (make-hosted-content-source "localhost" 8383)
                          opts)]
          (as-expected-session-config? (session->map sess) opts)))
      (testing "with hosted content source using content base"
        (with-open [sess (create-session
                          db (make-hosted-content-source "localhost" 8383
                                                         {:content-base "UrukDB"})
                          opts)]
          (as-expected-session-config? (session->map sess) opts)))
      (testing "with hosted content source using user, password, content-base"
        (with-open [sess (create-session
                          db (make-hosted-content-source "localhost" 8383
                                                         {:user "uruk-tester"
                                                          :password "password"
                                                          :content-base "UrukDB"})
                          opts)]
          (as-expected-session-config? (session->map sess) opts)))
      (testing "with hosted content source using user and password"
        (with-open [sess (create-session
                          db (make-hosted-content-source "localhost" 8383
                                                         {:user "uruk-tester"
                                                          :password "password"})
                          opts)]
          (as-expected-session-config? (session->map sess) opts)))
      ;; TODO once we want to delve into extreme complexity of ConnectionProvider
      ;; (as-expected-session-config? (session->map
      ;;                                (create-session
      ;;                                 db (make-cp-content-source ...)
      ;;                                 opts))
      ;;                               opts)
      )))

(deftest accept-only-valid-session-config
  (testing "Session creation should throw an error if passed an invalid/unrecognized configuration option"
    (is (thrown? IllegalArgumentException
                 (create-session db {:derp "invalid!"})))
    (is (thrown? IllegalArgumentException
                 (create-session db {:transaction-timeout 234 :derp "invalid!"})))
    (is (thrown? IllegalArgumentException
                 (create-session db
                                 (make-uri-content-source "xdbc://localhost:8383/")
                                 {:transaction-timeout 234 :derp "invalid!"})))))


(deftest default-sessions-created-as-expected
  (testing "We should be able to create default sessions from all kinds of content sources."
    (testing "...URI content sources"
      (is (instance? com.marklogic.xcc.impl.SessionImpl
                     (create-default-session (make-uri-content-source (:uri db)))))
      (is (= "default session logger" (-> (make-uri-content-source (:uri db)
                                                                   {:default-logger (Logger/getLogger "default session logger")})
                                          create-default-session
                                          .getLogger
                                          .getName)))
      (is (= "non-default session logger" (-> (make-uri-content-source (:uri db)
                                                                       {:default-logger (Logger/getLogger "default session test")})
                                              (create-default-session {:logger (Logger/getLogger "non-default session logger")})
                                              .getLogger
                                              .getName))))
    (testing "...hosted content sources"
      (is (instance? com.marklogic.xcc.impl.SessionImpl
                     (create-default-session (make-hosted-content-source "localhost" 8383
                                                                         {:content-base "UrukDB"}))))
      (is (= "default session logger" (-> (make-hosted-content-source "localhost" 8383
                                                                      {:default-logger (Logger/getLogger "default session logger")})
                                          create-default-session
                                          .getLogger
                                          .getName)))
      (is (= "non-default session logger" (-> (make-hosted-content-source "localhost" 8383
                                                                          {:default-logger (Logger/getLogger "default session test")})
                                              (create-default-session {:logger (Logger/getLogger "non-default session logger")})
                                              .getLogger
                                              .getName))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Content creation options
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest default-content-options
  (testing "Passing nothing to content creation options should return default options"
    (is (= {:encoding "UTF-8"
            :format :none,
            :permissions []
            :buffer-size -1
            :locale nil
            :repair-level :default
            :resolve-buffer-size 0
            :collections []
            :language nil
            :resolve-entities false
            :graph nil
            :quality 0
            :namespace nil
            :temporal-collection nil}
           (content-creation-options->map (content-creation-options {}))))))

(deftest content-options-roundtrip
  (testing "Round-trip options through creation and description"
    (is (let [opts {:buffer-size 400
                    :collections ["my-collection" "another-collection"]
                    :encoding "ASCII"
                    :format :text
                    :graph "my-graph"
                    :language "fr"
                    :locale (Locale. "ru")
                    :namespace "my-ns"
                    :permissions [{"such-and-such-role" :insert}
                                  {"such-and-such-role" :update}]
                    ;; :placement-keys
                    :quality 20
                    :repair-level :full
                    :resolve-buffer-size 20
                    :resolve-entities true
                    :temporal-collection "my-temp"}]
          (= opts
             (content-creation-options->map (content-creation-options opts)))))))

(deftest accept-only-valid-content-options
  (testing "Content Options that don't exist must raise an error"
    (is (thrown? java.lang.IllegalArgumentException
                 (content-creation-options {:does-not-exist "irrelevant"})))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Variables
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest variables
  (testing "Variables must be created as specified."
    (testing "...document variables passed to request object must be documents, not strings"
      (is (instance? com.marklogic.xcc.types.impl.DocumentImpl
                     (-> (with-open [session (create-session db)]
                           (#'uruk.core/make-request-obj
                            (.newAdhocQuery session "hello world")
                            nil {:derp {:value "<foo/>"
                                        :type :document}}))
                         .getVariables
                         first
                         .getValue))))

    (testing "...default variables map must convert to xs:string values"
      (is (= "xs:string" (with-open [session (create-session db)]
                           (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                                                  declare variable $my-variable external;
                                                                  $my-variable"
                                                         {:variables {"my-variable" "test string"
                                                                      :a "a"}
                                                          :types :raw})))))
      (is (= "a" (with-open [session (create-session db)]
                   (execute-xquery session "xquery version \"1.0-ml\";
                                            declare variable $my-variable external;
                                            declare variable $a external;
                                            $a"
                                   {:variables {"my-variable" "test string"
                                                :a "a"}
                                    :shape :single!})))))

    (testing "...variables passed `as-is?` must be passed untouched to MarkLogic"
      (testing "...with shorthand variable format"
        (is (= "test string" (with-open [session (create-session db)]
                               (execute-xquery session "xquery version \"1.0-ml\";
                                                      declare variable $my-variable external;
                                                      $my-variable"
                                               {:variables {"my-variable" "test string"}
                                                :shape :single!
                                                :as-is? :true})))))

      (testing "...with value-declared variable format"
        (is (= "test string" (with-open [session (create-session db)]
                               (execute-xquery session "xquery version \"1.0-ml\";
                                                      declare variable $my-variable external;
                                                      $my-variable"
                                               {:variables {"my-variable" {:value "test string"}}
                                                :shape :single!
                                                :as-is? :true}))))))

    (testing "...Clojure booleans must convert as instructed to XDM/XS boolean types"
      (is (= "xs:boolean"
             (with-open [session (create-session db)]
               (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                                      declare variable $my-variable external;
                                                      $my-variable"
                                             {:types :raw
                                              :variables {"my-variable" {:value false
                                                                         :type :xs-boolean}}})))))
      (is (= "boolean-node()" (result->type
                               (with-open [session (create-session db)]
                                 (execute-xquery session "xquery version \"1.0-ml\";
                                                          declare variable $my-variable external;
                                                          $my-variable"
                                                 {:variables {"my-variable" {:value false
                                                                             :type :boolean-node}}
                                                  :types :raw})))))
      (is (false? (with-open [session (create-session db)]
                    (execute-xquery session "xquery version \"1.0-ml\";
                                             declare variable $my-variable as boolean-node() external;
                                             $my-variable"
                                    {:variables {"my-variable" {:value false
                                                                :type :boolean-node}}
                                     :shape :single!}))))

      (is (false? (with-open [session (create-session db)]
                    (execute-xquery session "xquery version \"1.0-ml\";
                                             declare variable $my-variable as xs:boolean external;
                                             $my-variable"
                                    {:shape :single!
                                     :variables {"my-variable" {:value false
                                                                :type :xs-boolean}}}))))

      (is (true? (with-open [session (create-session db)]
                   (execute-xquery session "xquery version \"1.0-ml\";
                                declare variable $my-variable as boolean-node() external;
                                $my-variable"
                                   {:variables {"my-variable" {:value true
                                                               :type :boolean-node}}
                                    :shape :single!})))))

    (testing "...Clojure numeric types must convert as instructed to appropriate XS types"
      (testing "...xs:float"
        (is (= "xs:float"
               (with-open [session (create-session db)]
                 (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                         declare variable $my-variable external;
                                         $my-variable"
                                               {:variables {"my-variable" {:value 0.5
                                                                           :type :xs-float}}
                                                :types :raw}))))))

      (testing "...xs:double"
        (is (= "xs:double"
               (with-open [session (create-session db)]
                 (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                         declare variable $my-variable external;
                                         $my-variable"
                                               {:variables {"my-variable" {:value 0.5
                                                                           :type :xs-double}}
                                                :types :raw}))))))

      (testing "...xs:decimal"
        (is (= "xs:decimal"
               (with-open [session (create-session db)]
                 (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                         declare variable $my-variable external;
                                         $my-variable"
                                               {:variables {"my-variable" {:value 0.5
                                                                           :type :xs-decimal}}
                                                :types :raw})))))))

    (testing "...must be able to create CTS types from Clojure data structures"
      (is (= "cts:point"
             (with-open [session (create-session db)]
               (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                                      declare variable $my-variable external;
                                                      $my-variable"
                                             {:types :raw
                                              :variables {"my-variable" {:value [12 5]
                                                                         :type :cts-point}}})))))

      (is (= "cts:polygon"
             (with-open [session (create-session db)]
               (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                                      declare variable $my-variable external;
                                                      $my-variable"
                                             {:types :raw
                                              :variables {"my-variable" {:value [[1 1] [0 0] [1 0]]
                                                                         :type :cts-polygon}}})))))

      (is (= "cts:circle"
             (with-open [session (create-session db)]
               (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                                      declare variable $my-variable external;
                                                      $my-variable"
                                             {:types :raw
                                              :variables {"my-variable" {:value [5 [0 0]]
                                                                         :type :cts-circle}}})))))

      (is (= "cts:box"
             (with-open [session (create-session db)]
               (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                                      declare variable $my-variable external;
                                                      $my-variable"
                                             {:types :raw
                                              :variables {"my-variable" {:value [1 2 3 4]
                                                                         :type :cts-box}}}))))))

    (testing "...must be able to create miscellaneous XS types from Clojure data"
      (testing "...QNames"
        (is (= "xs:QName"
               (with-open [session (create-session db)]
                 (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                         declare variable $my-variable external;
                                         $my-variable"
                                               {:variables {"my-variable" {:value "test-qname"
                                                                           :type :xs-qname}}
                                                :types :raw}))))))

      (testing "...URIs"
        (is (= "xs:anyURI"
               (result->type (with-open [session (create-session db)]
                               (execute-xquery session "xquery version \"1.0-ml\";
                                  declare variable $my-variable external;
                                  $my-variable"
                                               {:types :raw
                                                :variables {"my-variable" {:value "http://www.google.com"
                                                                           :type :xs-any-uri}}})))))

        (is (= "http://www.google.com"
               (with-open [session (create-session db)]
                 (execute-xquery session "xquery version \"1.0-ml\";
                                                      declare variable $my-variable external;
                                                      $my-variable"
                                 {:shape :single!
                                  :variables {"my-variable" {:value "http://www.google.com"
                                                             :type :xs-any-uri}}})))))

      (testing "...null nodes, which can't be created as top-level objects (detail of exception should be 'XDMP-JSONDOC: Document is not JSON')"
        (is (thrown? com.marklogic.xcc.exceptions.XQueryException
                     (with-open [session (create-session db)]
                       (execute-xquery session "xquery version \"1.0-ml\";
                                  declare variable $my-variable external;
                                  $my-variable"
                                       {:types :raw
                                        :variables {"my-variable" {:value "x"
                                                                   :type :null-node}}})))))

      (testing "...elements"
        (is (= "element()"
               (with-open [session (create-session db)]
                 (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                  declare variable $my-variable external;
                                  $my-variable"
                                               {:types :raw
                                                :variables {"my-variable" {:value "<foo/>"
                                                                           :type :element}}}))))))

      (testing "...array-nodes"
        (is (= "array-node()"
               (with-open [session (create-session db)]
                 (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                  declare variable $my-variable external;
                                  $my-variable"
                                               {:types :raw
                                                :variables {"my-variable" {:value ["one" "two"]
                                                                           :type :array-node}}}))))))

      (testing "...documents (really document nodes)"
        (is (= "document-node()"
               (with-open [session (create-session db)]
                 (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                  declare variable $my-variable external;
                                  $my-variable"
                                               {:types :raw
                                                :variables {"my-variable" {:value "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<foo/>"
                                                                           :type :document}}}))))))

      (testing "...number nodes"
        (is (= "number-node()"
               (with-open [session (create-session db)]
                 (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                  declare variable $my-variable external;
                                  $my-variable"
                                               {:types :raw
                                                :variables {"my-variable" {:value 1
                                                                           :type :number-node}}}))))))

      (testing "...XS Strings"
        (is (= "xs:string"
               (with-open [session (create-session db)]
                 (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                  declare variable $my-variable external;
                                  $my-variable"
                                               {:types :raw
                                                :variables {"my-variable" {:value "i am a string"
                                                                           :type :xs-string}}}))))))

      (testing "...JSON objects"
        (is (= "json:object"
               (with-open [session (create-session db)]
                 (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                                        declare variable $my-variable external;
                                                        $my-variable"
                                               {:types :raw
                                                :variables {"my-variable" {:value {:json-key "json value"}
                                                                           :type :js-object}}}))))))

      (testing "...JSON arrays"
        (is (= "json:array"
               (with-open [session (create-session db)]
                 (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                                        declare variable $my-variable external;
                                                        $my-variable"
                                               {:types :raw
                                                :variables {"my-variable" {:value ["a" "b" 1]
                                                                           :type :js-array}}}))))))

      (testing "...(JSON) object nodes"
        ;; TODO I'd like to also be able to pass a JSON object that's a
        ;; value, but that comes through as "text"
        (is (= "object-node()"
               (with-open [session (create-session db)]
                 (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                                        declare variable $my-variable external;
                                                        $my-variable"
                                               {:types :raw
                                                :variables {"my-variable" {:value {:key "val"}
                                                                           :type :object-node}}})))))))))

;; ;; TODO test all (minus unknown/as-yet-unused) variable types can be sent correctly:
;; :binary :attribute :variable :duration :node :processing-instruction
;; :xs-date :xs-hex-binary :xs-gday :xs-day-time-duration :xs-date-time :xs-gyear :xs-duration :xs-base64-binary :xs-gmonth :xs-gmonth-day :xs-integer :comment :xs-gyear-month :xs-untyped-atomic :xs-time :xs-year-month-duration

;; XXX done:
;; :document
;;:null-node
;; :boolean-node
;; :xs-qname
;; :xs-boolean
;; :xs-decimal :xs-double  :xs-float
;; :xs-any-uri
;; :cts-circle  :cts-point :cts-polygon :cts-box
;; :element
;; :array-node
;; :sequence
;; :number-node
;;  :xs-string
;; :js-object
;; :js-array
;; :object-node

;; XXX shouldn't be sent:
;; :text


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Invalid query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest error-on-invalid-query
  (testing "An error must be thrown if MarkLogic is passed an invalid query."
    (is (thrown? com.marklogic.xcc.exceptions.XQueryException
                 (with-open [sess (create-session db)]
                   (execute-xquery sess "let $uri := xdmp:get-request-field(\"uri\")returnif"))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Shape
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest shapes
  (testing "Shape parameter must force result values to conform"
    (testing "...:single shape must return one result"
      (is (< 1 (count (with-open [sess (create-session db)]
                        (execute-xquery sess "array-node {1,2,3}"
                                        {:shape :single}))))))

    (testing "...:single! shape must return one result"
      (is (< 1 (count (with-open [sess (create-session db)]
                        (execute-xquery sess "array-node {1,2,3}"
                                        {:shape :single!}))))))

    (testing "...query used for other shape tests must return list of one element"
      (is (= 1 (count (with-open [sess (create-session db)]
                        (execute-xquery sess "array-node {1,2,3}"))))))

    (testing "...:single! must throw an exception if multiple results are returned"
      (is (thrown? clojure.lang.ExceptionInfo
                   (with-open [sess (create-session db)]
                     (execute-xquery sess "xdmp:get-current-roles()"
                                     {:shape :single!})))))

    (testing "...:none shaped result must be nil"
      (is (nil?
           (with-open [sess (create-session db)]
             (execute-xquery sess "xdmp:get-current-roles()"
                             {:shape :none})))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; TODO Transactions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Element insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest element-insertion
  (testing "Clojure XML Elements must be insertable as documents"
    ;; Insert a document
    (with-open [session (create-session db)]
      (insert-element session
                      "/content-factory/new-doc"
                      (clojure.data.xml/element :foo)))
    ;; Did that insert work?
    (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<foo/>"
           (with-open [session (create-session db)]
             (execute-xquery session
                             "xquery version \"1.0-ml\"; fn:doc('/content-factory/new-doc');"
                             {:shape :single}))))
    ;; Clean up inserted document so we don't affect the next test run
    (with-open [session (create-session db)]
      (execute-xquery session
                      "xquery version \"1.0-ml\"; xdmp:document-delete('/content-factory/new-doc');"
                      {:shape :single})))

  (testing "Insert-element must accept content creation options"
    ;; Insert a document with various options
    (with-open [session (create-session db)]
      (insert-element session
                      "/content-factory/new-doc"
                      (clojure.data.xml/element :foo)
                      {:quality 2})) ;; TODO test other options too?
    ;; Did our options get included in that doc?
    (is (= 2
           (with-open [session (create-session db)]
             (execute-xquery session
                             "xquery version \"1.0-ml\";
                              xdmp:document-get-quality('/content-factory/new-doc');"
                             {:shape :single}))))
    ;; Clean up inserted document so we don't affect the next test run
    (with-open [session (create-session db)]
      (execute-xquery session
                      "xquery version \"1.0-ml\";
                       xdmp:document-delete('/content-factory/new-doc');"
                      {:shape :single}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; String insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest string-insertion
  (testing "Strings must be insertable as documents"
    ;; Insert a document
    (with-open [session (create-session db)]
      (insert-string session
                     "/content-factory/new-json-doc"
                     "{\"a\": \"b\"}"))
    ;; Did that insert work?
    (is (= {"a" "b"}
           (with-open [session (create-session db)]
             (execute-xquery session
                             "xquery version \"1.0-ml\"; fn:doc('/content-factory/new-json-doc');"
                             {:shape :single}))))
    ;; Clean up inserted document so we don't affect the next test run
    (with-open [session (create-session db)]
      (execute-xquery session
                      "xquery version \"1.0-ml\"; xdmp:document-delete('/content-factory/new-json-doc');"
                      {:shape :single})))

  (testing "Insert-string must accept content creation options"
    ;; Insert a document with various options
    (with-open [session (create-session db)]
      (insert-string session
                     "/content-factory/new-json-doc"
                     "{\"a\": \"b\"}"
                     {:quality 2})) ;; TODO test other options too?
    ;; Did our options get included in that doc?
    (is (= 2
           (with-open [session (create-session db)]
             (execute-xquery session
                             "xquery version \"1.0-ml\";
                              xdmp:document-get-quality('/content-factory/new-json-doc');"
                             {:shape :single}))))
    ;; Clean up inserted document so we don't affect the next test run
    (with-open [session (create-session db)]
      (execute-xquery session
                      "xquery version \"1.0-ml\";
                       xdmp:document-delete('/content-factory/new-json-doc');"
                      {:shape :single}))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; TODO security options
;; (requires SSLContext)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Content Source
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (comment
;;   (session->map (create-session {:uri "xdbc://localhost:8383/"
;;                                     :user "uruk-tester" :password "password"
;;                                     :content-base "UrukDB"}
;;                                    {} (uri-content-source "xdbc://localhost:8383/"
;;                                                           (security-options TODO)))))

(deftest content-source-creation-with-uri
  (testing "content source creation from just a URI"
    (let [cs (make-uri-content-source "xdbc://localhost:8383/")]
      (and (instance? ContentSource cs)
           (= 8383 (.getPort (.getConnectionProvider cs)))
           (= "localhost" (.getHostName (.getConnectionProvider cs)))))))

;; TODO content source from URI and securityoptions
;; (deftest content-source-creation-with-uri-and-security-options
;;   (testing "content source creation from just a URI"
;;     (let [cs (uri-content-source "xdbc://localhost:8383/"
;;                                  (make-security-options ...))]
;;       (and (instance? ContentSource cs)
;;            (= 8383 (.getPort (.getConnectionProvider (uri-content-source "xdbc://localhost:8383/"))))
;;            (= "localhost" (.getHostName (.getConnectionProvider (uri-content-source "xdbc://localhost:8383/"))))))))

(deftest content-source-creation-with-host-and-port
  (testing "content source creation from host and port"
    (let [cs (make-hosted-content-source "localhost" 8383)]
      (and (instance? ContentSource cs)
           (= 8383 (.getPort (.getConnectionProvider cs)))
           (= "localhost" (.getHostName (.getConnectionProvider cs)))))))

(deftest content-source-creation-with-host-port-user-pwd
  (testing "content source creation from host and port"
    (let [cs (make-hosted-content-source "localhost" 8383
                                         {:user "uruk-tester" :password "password"})]
      (and (instance? ContentSource cs)
           (= 8383 (.getPort (.getConnectionProvider cs)))
           (= "localhost" (.getHostName (.getConnectionProvider cs)))))))

;; TODO with SSLContext
;; (comment
;;   (describe-session-options (create-session {:uri "xdbc://localhost:8383/"
;;                                     :user "uruk-tester" :password "password"
;;                                     :content-base "UrukDB"}
;;                                    (hosted-content-source "localhost" 8383
;;                                                           "uruk-tester" "x" "UrukDB"
;;                                                           (security-options TODO))
;;                                    {}))

;;   (describe-session-options (create-session {:uri "xdbc://localhost:8383/"
;;                                     :user "uruk-tester" :password "password"
;;                                     :content-base "UrukDB"}
;;                                    (managed-content-source connection-provider ;; TODO implement https://docs.marklogic.com/javadoc/xcc/com/marklogic/xcc/spi/ConnectionProvider.html interface
;;                                                            "uruk-tester" "x" "UrukDB")
;;                                    {})))

(deftest content-src-options
  (testing "Content Source preemptive-authentication settings"
    (is (true? (.isAuthenticationPreemptive (make-uri-content-source "xdbc://localhost:8383/"
                                                                     {:preemptive-auth true}))))
    (is (false? (.isAuthenticationPreemptive (make-uri-content-source "xdbc://localhost:8383/"
                                                                      {:preemptive-auth false}))))
    (is (true? (.isAuthenticationPreemptive (make-hosted-content-source "localhost" 8383
                                                                        {:preemptive-auth true}))))
    (is (false? (.isAuthenticationPreemptive (make-hosted-content-source "localhost" 8383
                                                                         {:preemptive-auth false}))))
    ;; TODO with make-cp-content-source, once we want to delve into
    ;; the extreme complexity of ConnectionProvider
    )
  (testing "Content Source default Logger settings"
    (is (= "foo" (.getName (.getDefaultLogger (make-uri-content-source
                                               "xdbc://localhost:8383/"
                                               {:default-logger (Logger/getLogger "foo")})))))
    (is (= "bar" (.getName (.getDefaultLogger (make-hosted-content-source
                                               "localhost" 8383
                                               {:default-logger (Logger/getLogger "bar")})))))
    ;; TODO with make-cp-content-source, once we want to delve into
    ;; the extreme complexity of ConnectionProvider
    ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Type tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest type-conversion
  (testing "Types roundtrip through XQuery with expected conversions"
    (testing "...JSON interface types"
      (testing "......array-node type"
        (with-open [session (create-session db)]
          (is (= "array-node()"
                 (result->type (execute-xquery session "array-node {1,2,3}"
                                               {:types :raw})))))

        (with-open [session (create-session db)]
          (is (= [1 2 3]
                 (execute-xquery session "array-node {1,2,3}"
                                 {:shape :single!})))))

      (testing "......boolean-node type"
        (with-open [session (create-session db)]
          (is (= "boolean-node()"
                 (result->type (execute-xquery session "boolean-node{false()}"
                                               {:types :raw})))))
        (with-open [session (create-session db)]
          (is (false? (execute-xquery session "boolean-node{false()}"
                                      {:shape :single!})))))

      (testing "......null-node type"
        (with-open [session (create-session db)]
          (is (= "null-node()"
                 (result->type (execute-xquery session "null-node {}"
                                               {:types :raw})))))
        (with-open [session (create-session db)]
          (is (nil? (execute-xquery session "null-node {}"
                                    {:shape :single!})))))

      (testing "......number-node type"
        (with-open [session (create-session db)]
          (is (= "number-node()"
                 (result->type (execute-xquery session "number-node {1}"
                                               {:types :raw})))))
        (with-open [session (create-session db)]
          (is (= 1 (execute-xquery session "number-node {1}"
                                   {:shape :single!})))))

      (testing "......object-node type"
        (with-open [session (create-session db)]
          (is (= "object-node()"
                 (result->type (execute-xquery session "xquery version \"1.0-ml\";
                                      let $object := json:object()
                                      let $_ := map:put($object,\"a\",111)
                                      return xdmp:to-json($object)" {:types :raw})))))
        (with-open [session (create-session db)]
          (is (= {"a" 111}
                 (execute-xquery session "xquery version \"1.0-ml\";
                                      let $object := json:object()
                                      let $_ := map:put($object,\"a\",111)
                                      return xdmp:to-json($object)"
                                 {:shape :single!}))))))


    (testing "...JSON types"
      (testing "......JSON array type"
        (with-open [session (create-session db)]
          (is (= "json:array"
                 (result->type (execute-xquery session "xquery version \"1.0-ml\";
                            json:array(
                             <json:array xmlns:json=\"http://marklogic.com/xdmp/json\"
                             xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"
                             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">
                             <json:value xsi:type=\"xs:string\">hello</json:value>
                             <json:value xsi:type=\"xs:string\">world</json:value>
                             <json:array>
                             <json:value xsi:type=\"xs:string\">one</json:value>
                             <json:value xsi:type=\"xs:string\">two</json:value>
                             </json:array>
                             </json:array>
                            )" {:types :raw})))))
        (with-open [session (create-session db)]
          (is (= ["hello" "world" ["one" "two"]]
                 (execute-xquery session
                                 "xquery version \"1.0-ml\";
                            json:array(
                             <json:array xmlns:json=\"http://marklogic.com/xdmp/json\"
                             xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"
                             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">
                             <json:value xsi:type=\"xs:string\">hello</json:value>
                             <json:value xsi:type=\"xs:string\">world</json:value>
                             <json:array>
                             <json:value xsi:type=\"xs:string\">one</json:value>
                             <json:value xsi:type=\"xs:string\">two</json:value>
                             </json:array>
                             </json:array>
                            )" {:shape :single!})))))

      (testing "......JSON object type"
        (with-open [session (create-session db)]
          (is (= "json:object"
                 (result->type (execute-xquery session "json:object()"
                                               {:types :raw})))))
        (with-open [session (create-session db)]
          (is (= {}
                 (execute-xquery session "json:object()"
                                 {:shape :single!})))))))

  (testing "...CTS types"
    (testing "......CTS box type"
      (with-open [session (create-session db)]
        (is (= "cts:box"
               (result->type (execute-xquery session "cts:box(45, -122, 78, 30)"
                                             {:types :raw})))))
      (with-open [session (create-session db)]
        (is (= "[45, -122, 78, 30]"
               (execute-xquery session "cts:box(45, -122, 78, 30)"
                               {:shape :single!})))))

    (testing "......CTS circle type"
      (with-open [session (create-session db)]
        (is (= "cts:circle"
               (result->type (execute-xquery session "cts:circle(20, cts:point(37.655983, -122.425525))"
                                             {:types :raw})))))

      (with-open [session (create-session db)]
        (is (= "@20 37.655983,-122.42553"
               (execute-xquery session "cts:circle(20, cts:point(37.655983, -122.425525))"
                               {:shape :single!})))))

    (testing "......CTS point type"
      (with-open [session (create-session db)]
        (is (= "cts:point"
               (result->type (execute-xquery session "cts:point(37.655983, -122.425525)"
                                             {:types :raw})))))

      (with-open [session (create-session db)]
        (is (= "37.655983,-122.42553"
               (execute-xquery session "cts:point(37.655983, -122.425525)"
                               {:shape :single!})))))

    (testing "......CTS polygon type"
      (with-open [session (create-session db)]
        (is (= "cts:polygon"
               (result->type
                (execute-xquery
                 session
                 "(: this polygon approximates the 94041 zip code :)
                  let $points := (cts:point(0.373899653086420E+02, -0.122078578406509E+03),
                                  cts:point(0.373765400000000E+02, -0.122063772000000E+03),
                                  cts:point(0.373781400000000E+02, -0.122067972000000E+03),
                                  cts:point(0.373825650000000E+02, -0.122068365000000E+03),
                                  cts:point(0.373797400000000E+02, -0.122072172000000E+03),
                                  cts:point(0.373899400000000E+02, -0.122092573000000E+03),
                                  cts:point(0.373941400000000E+02, -0.122095573000000E+03),
                                  cts:point(0.373966400000000E+02, -0.122094173000000E+03),
                                  cts:point(0.373958400000000E+02, -0.122092373000000E+03),
                                  cts:point(0.374004400000000E+02, -0.122091273000000E+03),
                                  cts:point(0.374004400000000E+02, -0.122091273000000E+03),
                                  cts:point(0.373873400000000E+02, -0.122057872000000E+03),
                                  cts:point(0.373873400000000E+02, -0.122057872000000E+03),
                                  cts:point(0.373854400000000E+02, -0.122052672000000E+03),
                                  cts:point(0.373833400000000E+02, -0.122053372000000E+03),
                                  cts:point(0.373819400000000E+02, -0.122057572000000E+03),
                                  cts:point(0.373775400000000E+02, -0.122060872000000E+03),
                                  cts:point(0.373765400000000E+02, -0.122063772000000E+03) )
                                                    return
                                                    cts:polygon($points)"
                 {:types :raw})))))


      (with-open [session (create-session db)]
        (is (= "37.389965,-122.07858 37.37654,-122.06377 37.37814,-122.06797 37.382565,-122.06837 37.37974,-122.07217 37.38994,-122.09257 37.39414,-122.09557 37.39664,-122.09417 37.39584,-122.09237 37.40044,-122.09127 37.40044,-122.09127 37.38734,-122.05787 37.38734,-122.05787 37.38544,-122.05267 37.38334,-122.05337 37.38194,-122.05757 37.37754,-122.06087 37.37654,-122.06377 37.389965,-122.07858"
               (execute-xquery session "(: this polygon approximates the 94041 zip code :)
                                      let $points := (cts:point(0.373899653086420E+02, -0.122078578406509E+03),
                                        cts:point(0.373765400000000E+02, -0.122063772000000E+03),
                                        cts:point(0.373781400000000E+02, -0.122067972000000E+03),
                                        cts:point(0.373825650000000E+02, -0.122068365000000E+03),
                                        cts:point(0.373797400000000E+02, -0.122072172000000E+03),
                                        cts:point(0.373899400000000E+02, -0.122092573000000E+03),
                                        cts:point(0.373941400000000E+02, -0.122095573000000E+03),
                                        cts:point(0.373966400000000E+02, -0.122094173000000E+03),
                                        cts:point(0.373958400000000E+02, -0.122092373000000E+03),
                                        cts:point(0.374004400000000E+02, -0.122091273000000E+03),
                                        cts:point(0.374004400000000E+02, -0.122091273000000E+03),
                                        cts:point(0.373873400000000E+02, -0.122057872000000E+03),
                                        cts:point(0.373873400000000E+02, -0.122057872000000E+03),
                                        cts:point(0.373854400000000E+02, -0.122052672000000E+03),
                                        cts:point(0.373833400000000E+02, -0.122053372000000E+03),
                                        cts:point(0.373819400000000E+02, -0.122057572000000E+03),
                                        cts:point(0.373775400000000E+02, -0.122060872000000E+03),
                                        cts:point(0.373765400000000E+02, -0.122063772000000E+03) )
                                      return
                                      cts:polygon($points)"
                               {:shape :single!}))))))

  (testing "...XDM types"
    (let [path-to-img (System/getenv "URUK_TEST_IMG_PATH")] ;; FIXME path on your system
      (testing "......XDMBinary"
        (is (= "binary()"
               (result->type (with-open [session (create-session db)]
                               (execute-xquery session (str "xquery version \"1.0-ml\";
                                                             xdmp:external-binary(\""
                                                            path-to-img "\");")
                                               {:types :raw})))))
        (is (= (Class/forName "[B")
               (.getClass (with-open [sess (create-session db)]
                            (execute-xquery sess (str "xquery version \"1.0-ml\";
                                                       xdmp:external-binary(\""
                                                      path-to-img "\");")
                                            {:shape :single!}))))))))

  (testing "...XS types"
    (testing "......XSAnyURI"
      (with-open [session (create-session db)]
        (is (= "xs:anyURI"
               (result->type (execute-xquery session "fn:resolve-uri(\"hello/goodbye.xml\",
                                                                     \"http://mycompany/default.xqy\")"
                                             {:types :raw})))))

      (with-open [session (create-session db)]
        (is (= "http://mycompany/hello/goodbye.xml"
               (execute-xquery session "fn:resolve-uri(\"hello/goodbye.xml\",
                                                       \"http://mycompany/default.xqy\")"
                               {:shape :single!})))))

    (testing "......XSBase64Binary"
      (is (= "xs:base64Binary"
             (result->type (with-open [session (create-session db)]
                             (execute-xquery session "xs:base64Binary(\"bmhnY2p2\")"
                                             {:types :raw})))))
      (is (= (Class/forName "[B")
             (.getClass (with-open [session (create-session db)]
                          (execute-xquery session "xs:base64Binary(\"bmhnY2p2\")"
                                          {:shape :single!}))))))

    (testing "......XSBoolean"
      (with-open [session (create-session db)]
        (is (= "xs:boolean"
               (result->type (execute-xquery session "fn:doc-available(\"derp\")"
                                             {:types :raw}))))
        (is (false? (execute-xquery session "fn:doc-available(\"derp\")" {:shape :single!})))
        (is (= "xs:boolean"
               (result->type (execute-xquery session "fn:true()"
                                             {:types :raw}))))
        (is (true? (execute-xquery session "fn:true()" {:shape :single!})))))

    (testing "......XSDate"
      (with-open [session (create-session db)]
        (is (= "xs:date"
               (result->type (execute-xquery session "fn:adjust-date-to-timezone(xs:date(\"2002-03-07-07:00\"),())"
                                             {:types :raw}))))
        (is (= "2002-03-07"
               (execute-xquery session "fn:adjust-date-to-timezone(xs:date(\"2002-03-07-07:00\"),
                                                           ())"
                               {:shape :single!})))))

    (testing "......XSDateTime"
      (with-open [session (create-session db)]
        (is (= "xs:dateTime"
               (result->type (execute-xquery session "fn:adjust-dateTime-to-timezone(xs:dateTime(\"2002-03-07T10:00:00\"), ())"
                                             {:types :raw}))))
        (is (= "2002-03-07T10:00:00"
               (execute-xquery session "fn:adjust-dateTime-to-timezone(xs:dateTime(\"2002-03-07T10:00:00\"), ())"
                               {:shape :single!})))))

    (testing "......XSDayTimeDuration"
      (with-open [session (create-session db)]
        (is (= "xs:dayTimeDuration"
               (result->type (execute-xquery session "xquery version \"0.9-ml\"
                                                      fn:subtract-dateTimes-yielding-dayTimeDuration(fn:adjust-dateTime-to-timezone(xs:dateTime(\"2002-03-07T10:00:00\"), ()), xs:dateTime(\"2000-01-11T12:01:00.000Z\"))" ;; this fn removed in version 1.0; only used to get correct response type
                                             {:types :raw}))))
        (is (= "P785DT20H59M"
               (execute-xquery session "xquery version \"0.9-ml\"
                                        fn:subtract-dateTimes-yielding-dayTimeDuration(fn:adjust-dateTime-to-timezone(xs:dateTime(\"2002-03-07T10:00:00\"),
                                                                                                                      ()),
                                                                                       xs:dateTime(\"2000-01-11T12:01:00.000Z\"))"
                               {:shape :single!})))))

    (testing "......XSDecimal"
      (with-open [session (create-session db)]
        (is (= "xs:decimal" (result->type (execute-xquery session "fn:abs(-1.2)"
                                                          {:types :raw}))))
        (is (= 1.2 (execute-xquery session "fn:abs(-1.2)" {:shape :single!})))))

    (testing "......XSDouble"
      (with-open [session (create-session db)]
        (is (= "xs:double" (result->type (execute-xquery session "fn:number(-1.2)"
                                                         {:types :raw}))))
        (is (= -1.2 (execute-xquery session "fn:number(-1.2)" {:shape :single!})))
        (is (= "xs:double" (result->type (execute-xquery session "xs:double(-1.2)"
                                                         {:types :raw}))))
        (is (= -1.2 (execute-xquery session "xs:double(-1.2)" {:shape :single!})))))

    (testing "......XSDuration"
      (with-open [session (create-session db)]
        (is (= "xs:duration"
               (result->type (execute-xquery session "xs:duration(\"P3DT10H\")"
                                             {:types :raw}))))
        (is (= "P3DT10H"
               (execute-xquery session "xs:duration(\"P3DT10H\")" {:shape :single!})))))

    (testing "......XSFloat"
      (with-open [session (create-session db)]
        (is (= "xs:float" (result->type (execute-xquery session "xs:float(\"1\")"
                                                        {:types :raw}))))
        (is (= 1 (execute-xquery session "xs:float(\"1\")" {:shape :single!})))))

    (testing "......Gregorians!"
      (testing ".........XSGDay"
        (with-open [session (create-session db)]
          (is (= "xs:gDay" (result->type (execute-xquery session "xs:gDay('---08')"
                                                         {:types :raw}))))
          (is (= "---08" (execute-xquery session "xs:gDay('---08')" {:shape :single!})))))

      (testing ".........XSGMonth"
        (with-open [session (create-session db)]
          (is (= "xs:gMonth"
                 (result->type (execute-xquery session "xs:gMonth('--08')" {:types :raw}))))
          (is (= "--08" (execute-xquery session "xs:gMonth('--08')" {:shape :single!})))))

      (testing ".........XSGMonthDay"
        (with-open [session (create-session db)]
          (is (= "xs:gMonthDay"
                 (result->type (execute-xquery session "xs:gMonthDay('--08-20')"
                                               {:types :raw}))))
          (is (= "--08-20"
                 (execute-xquery session "xs:gMonthDay('--08-20')" {:shape :single!})))))

      (testing ".........XSGYear"
        (with-open [session (create-session db)]
          (is (= "xs:gYear"
                 (result->type (execute-xquery session "xs:gYear('2016')"
                                               {:types :raw}))))
          (is (= "2016" (execute-xquery session "xs:gYear('2016')" {:shape :single!})))))

      (testing ".........XSGYearMonth"
        (with-open [session (create-session db)]
          (is (= "xs:gYearMonth"
                 (result->type (execute-xquery session "xs:gYearMonth('2016-02')"
                                               {:types :raw}))))
          (is (= "2016-02" (execute-xquery session "xs:gYearMonth('2016-02')"
                                           {:shape :single!}))))))

    (testing ".........XSHexBinary"
      (with-open [session (create-session db)]
        (is (= "xs:hexBinary"
               (result->type (execute-xquery session "xs:hexBinary(\"74657374\")"
                                             {:types :raw}))))
        (is (= "74657374"
               (execute-xquery session "xs:hexBinary(\"74657374\")" {:shape :single!})))
        (is (= "xs:hexBinary"
               (result->type (execute-xquery session "data(xdmp:subbinary(binary { xs:hexBinary(\"DEADBEEF\") }, 3, 2))"
                                             {:types :raw}))))
        (is  (= "BEEF"
                (execute-xquery session "data(xdmp:subbinary(binary { xs:hexBinary(\"DEADBEEF\") }, 3, 2))"
                                {:shape :single!})))))

    (testing ".........XSInteger"
      (with-open [session (create-session db)]
        (is (= "xs:integer" (result->type (execute-xquery session "xdmp:databases()"
                                                          {:types :raw}))))
        (is (every? integer? (execute-xquery session "xdmp:databases()")))))

    (testing ".........XSQName"
      (with-open [session (create-session db)]
        (is (= "xs:QName"
               (result->type (execute-xquery session "fn:QName(\"http://www.example.com/example\", \"person\")"
                                             {:types :raw}))))
        (is (= "person"
               (execute-xquery session "fn:QName(\"http://www.example.com/example\", \"person\")"
                               {:shape :single!})))))

    (testing ".........XSString"
      (with-open [session (create-session db)]
        (is (= "xs:string"
               (result->type (execute-xquery session "\"hello world\""
                                             {:types :raw}))))
        (is (= "hello world"
               (execute-xquery session "\"hello world\"" {:shape :single!})))))

    (testing ".........XSTime"
      (with-open [session (create-session db)]
        (is (= "xs:time"
               (result->type (execute-xquery session "fn:adjust-time-to-timezone(xs:time(\"10:00:00\"))"
                                             {:types :raw}))))
        (is (= "10:00:00-01:00"
               (execute-xquery session "fn:adjust-time-to-timezone(xs:time(\"10:00:00\"), xs:dayTimeDuration(\"-PT1H\"))"
                               {:shape :single!})))))

    (testing ".........XSUntypedAtomic"
      (with-open [session (create-session db)]
        (is (= "xs:untypedAtomic"
               (result->type
                (execute-xquery session "let $x as xs:untypedAtomic*
                         := (xs:untypedAtomic(\"cherry\"),
                             xs:untypedAtomic(\"1\"),
                             xs:untypedAtomic(\"1\"))
                       return fn:distinct-values ($x)"
                                {:types :raw}))))
        (is (= '("cherry" "1")
               (execute-xquery session "let $x as xs:untypedAtomic*
                         := (xs:untypedAtomic(\"cherry\"),
                             xs:untypedAtomic(\"1\"),
                             xs:untypedAtomic(\"1\"))
                       return fn:distinct-values ($x)")))))

    (testing ".........XSYearMonthDuration"
      (with-open [session (create-session db)]
        (is (= "xs:yearMonthDuration"
               (result->type (execute-xquery session "xquery version \"0.9-ml\"
                                                      fn:subtract-dateTimes-yielding-yearMonthDuration(fn:current-dateTime(),
                                                                                                       xs:dateTime(\"2000-01-11T12:01:00.000Z\"))"
                                             {:types :raw}))))
        (is (= "P0M"
               (execute-xquery session "xquery version \"0.9-ml\"
                                        fn:subtract-dateTimes-yielding-yearMonthDuration(xs:dateTime(\"2000-01-15T12:01:00.000Z\"),
                                                                                         xs:dateTime(\"2000-01-11T12:01:00.000Z\"))"
                               {:shape :single!})))))))

(deftest custom-fns
  (testing "Custom response handling functions"
    (with-open [session (create-session db)]
      (instance? com.marklogic.xcc.types.impl.XsBooleanImpl
                 (execute-xquery session "fn:doc-available(\"derp\")"
                                 {:shape :single!
                                  :types {"xs:boolean" identity}})))
    (with-open [session (create-session db)]
      (= "false" (execute-xquery session "fn:doc-available(\"derp\")"
                                 {:shape :single!
                                  :types {"xs:boolean" #(.asString %)}})))
    (with-open [session (create-session db)]
      (= false (execute-xquery session "fn:doc-available(\"derp\")"
                               {:shape :single!
                                :types {"xs:boolean" #(.asBoolean %)}})))))

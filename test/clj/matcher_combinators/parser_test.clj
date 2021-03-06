(ns matcher-combinators.parser-test
  (:require [midje.sweet :refer :all :exclude [exactly contains]]
            [midje.experimental :refer [for-all]]
            [matcher-combinators.parser]
            [matcher-combinators.matchers :refer :all]
            [matcher-combinators.core :as core]
            [clojure.test.check.generators :as gen]
            [matcher-combinators.model :as model])
  (:import [java.net URI]))

(def gen-big-decimal
  (gen/fmap (fn [[integral fractional]]
              (BigDecimal. (str integral "." fractional)))
            (gen/tuple gen/int gen/pos-int)))

(def gen-big-int
  (gen/fmap #(* 1N %) gen/int))

(def gen-java-integer
  (gen/fmap #(Integer. %) gen/int))

(def gen-float
  (gen/fmap #(float %) gen/int))

(def gen-short
  (gen/fmap short gen/int))

(def gen-var (gen/elements (vals (ns-interns 'clojure.core))))

(def query-gen
  (gen/one-of [(gen/return nil) gen/string-alphanumeric]))

(def gen-uri
  ;; well actually generates a URL, but oh well
  (let [scheme          (gen/elements #{"http" "https"})
        authority       (gen/elements #{"www.foo.com" "www.bar.com:80"})
        path            (gen/one-of [(gen/return nil)
                                     (gen/fmap #(str "/" %) gen/string-alphanumeric)])
        args-validation (fn [[_scheme authority path query fragment]]
                          (not (or ;; a URI with just a scheme is invalid
                                (every? nil? (list authority path query fragment))
                                ;; a URI with just a scheme and fragment is invalid
                                (and (not (nil? fragment))
                                     (every? nil? (list authority path query))))))]

    (gen/fmap
     (fn [[scheme authority path query fragment]] (URI. scheme authority path query fragment))
     (gen/such-that
      args-validation
      (gen/tuple scheme authority path query-gen query-gen)))))

(def gen-scalar (gen/one-of [gen-java-integer
                             gen/int ;; really a Long
                             gen-short
                             gen/string
                             gen/symbol
                             gen-float
                             gen/double
                             gen/symbol-ns
                             gen/keyword
                             gen/boolean
                             gen/ratio
                             gen/uuid
                             gen-uri
                             gen-big-decimal
                             gen-big-int
                             gen/char
                             gen/bytes
                             gen-var]))

(defn gen-distinct-pair [element-generator]
  (gen/such-that (fn [[i j]] (not= i j)) (gen/tuple element-generator)))

(def gen-scalar-pair
  (gen-distinct-pair gen-scalar))

(facts "scalar values act as equals matchers"
  (for-all [i gen-scalar]
           {:num-tests 50}
           (core/match i i) => (core/match (equals i) i))

  (for-all [[i j] gen-scalar-pair]
           {:num-tests 50}
           (core/match i j) => (core/match (equals i) j)))

(fact "maps act as equals matcher"
  (fact
   (= (core/match (equals {:a (equals 10)}) {:a 10})
      (core/match (equals {:a 10}) {:a 10})
      (core/match {:a 10} {:a 10}))
    => truthy))

(fact "vectors act as equals matchers"
  (fact
   (= (core/match (equals [(equals 10)]) [10])
      (core/match (equals [10]) [10])
      (core/match [10] [10]))
    => truthy))

(def chunked-seq (seq [1 2 3]))
(fact "chunked sequences act as equals matchers"
  (core/match chunked-seq [10])) => truthy

(fact "lists also act as equals matchers"
  (fact
   (= (core/match (equals [(equals 10)]) [10])
      (core/match (equals '(10)) [10])
      (core/match '(10) [10])) => truthy))

(fact "`nil` is parsed as an equals"
  (fact
   (= (core/match (equals nil) nil)
      (core/match nil nil)) => truthy))

(fact "java classes are parsed as an equals"
  (fact
   (= (core/match (equals java.lang.String) java.lang.String)
      (core/match java.lang.String java.lang.String)) => truthy))

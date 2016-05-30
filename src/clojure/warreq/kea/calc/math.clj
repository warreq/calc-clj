(ns warreq.kea.calc.math
  (:require [clojure.math.numeric-tower :refer [expt]]
            [neko.notify :refer [toast]]
            [warreq.kea.calc.util :as u])
  (:import java.math.BigDecimal))

(defn rpn
  "Evaluate an expression composed in Reverse Polish Notation and return the
  result. `rpn` may optionally take a stack-based representation of an expression
  as a secondary parameter."
  ([e]
   (rpn e '()))
  ([e s]
   (if (empty? e)
     (first s)
     (if (number? (first e))
       (recur (rest e)
              (conj s (first e)))
       (recur (rest e)
              (conj (drop 2 s) (eval (conj (reverse (take 2 s)) (first e)))))))))

(defn floating-division [x y]
  (if (not= ^BigDecimal y BigDecimal/ZERO)
    (.divide ^BigDecimal x ^BigDecimal y 2 java.math.RoundingMode/HALF_EVEN)
    (do (u/vibrate! 500)
        (toast "Cannot divide by 0."))))

(defn op-alias [op]
  (case op
    "^" expt
    "÷" floating-division
    "×" *
    (resolve (symbol op))))


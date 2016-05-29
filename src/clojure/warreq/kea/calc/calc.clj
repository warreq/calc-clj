(ns warreq.kea.calc.calc
  (:require [clojure.math.numeric-tower :refer [expt]]
            [neko.notify :refer [toast]])
  (:import java.math.BigDecimal))

(def input (atom ""))

(def expression (atom []))

(def stack (atom '()))

(def err (atom ""))

(defn rpn
  "Evaluate an expression composed in Reverse Polish Notation and return the
  result. `rpn` may optionally take a stack as a separate parameter, which may
  contain a partially resolved expression."
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
    (.divide ^BigDecimal x ^BigDecimal y java.math.RoundingMode/HALF_UP)
    (do (reset! err "Cannot divide by 0.") nil)))

(defn op-alias [op]
  (case op
    "^" expt
    "÷" floating-division
    "×" *
    (resolve (symbol op))))

(defn return-handler
  [_]
  (when (> (count (deref input)) 0)
    (swap! stack conj (bigdec (read-string (deref input))))
    (reset! input "")))

(defn num-handler
  [n]
  (swap! input str n))

(defn op-handler
  [op]
  (when (> (count (deref input)) 0)
    (return-handler op))
  (when (>= (count (deref stack)) 2)
    (swap! expression conj (op-alias op))
    (when-let [result (rpn (apply list (deref expression)) (deref stack))]
      (toast (str result))
      (reset! expression [result])
      (reset! stack (drop 2 (deref stack)))
      (swap! stack conj (first (deref expression)))
      (reset! expression []))))

(defn clear-handler
  [_]
  (reset! input "")
  (reset! expression [])
  (reset! stack '()))

(defn backspace-handler
  [_]
  (let [cur (deref input)]
    (when (> (count cur) 0)
      (reset! input (.substring ^String cur 0 (- (count cur) 1)))
      (when (= "-" (deref input))
        (reset! input "")))))

(defn invert-handler
  [_]
  (let [cur (deref input)]
    (when (> (count cur) 0)
      (if (= (.charAt ^String cur 0) \-)
        (reset! input (.substring ^String cur 1 (count cur)))
        (reset! input (str "-" cur))))))

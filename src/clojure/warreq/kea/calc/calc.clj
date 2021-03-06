(ns warreq.kea.calc.calc
  (:require [neko.activity :refer [defactivity set-content-view!]]
            [neko.ui :refer [config get-screen-orientation]]
            [neko.intent :refer [intent]]
            [neko.resource :as res]
            [neko.debug :refer [*a]]
            [neko.find-view :refer [find-view]]
            [neko.threading :refer [on-ui]]
            [warreq.kea.calc.util :as u]
            [warreq.kea.calc.math :as math])
  (:import android.widget.EditText
           android.os.Bundle
           android.widget.TextView
           android.graphics.Typeface
           android.text.InputType
           me.grantland.widget.AutofitHelper))

;; We execute this function to import all subclasses of R class. This gives us
;; access to all application resources.
(res/import-all)

;; Calculator state =============================================================
(def stack (atom '()))

(defn input
  "Fetch the Input EditText field."
  []
  (find-view (*a) ::z))

(defn input-text
  "Fetch the current text from the Input EditText field"
  []
  (.toString (.getText (input))))

(defn toggle-edit-input
  "Enable numeric input to the Input widget via the device's keyboard."
  []
  (config ^android.widget.EditText (input) :input-type InputType/TYPE_CLASS_NUMBER))

(defn show-stack! []
  (let [a (*a)]
    (.startActivity ^android.app.Activity a (intent a '.StackView {}))))

;; Handler functions ============================================================
(defn return-handler
  [_]
  (when (> (count (input-text)) 0)
    (swap! stack conj (bigdec (read-string (input-text)))))
  (.setText (input) ""))

(defn num-handler
  [n]
  (.setText (input) (str (input-text) n)))

(defn op-handler
  [op]
  (when (> (count (input-text)) 0)
    (return-handler op))
  (when (>= (count (deref stack)) 2)
    (reset! stack (math/rpn [(math/op-alias op)] (deref stack)))))

(defn clear-handler
  [_]
  (.setText (input) "")
  (reset! stack '()))

(defn backspace-handler
  [_]
  (let [cur (input-text)]
    (when (> (count cur) 0)
      (.setText (input) (.substring ^String cur 0 (- (count cur) 1)))
      (when (= "-" (input-text))
        (.setText (input) "")))))

(defn invert-handler
  [_]
  (let [cur (input-text)]
    (when (> (count cur) 0)
      (if (= (.charAt ^String cur 0) \-)
        (.setText (input) (.substring ^String cur 1 (count cur)))
        (.setText (input) (str "-" cur))))))

(defn reduce-handler
  [op]
  (reset! stack (list (reduce (math/op-alias op) (deref stack)))))

;; Layout Definitions ===========================================================
(def op-column
  [(u/op-button "÷" op-handler {})
   (u/op-button "×" op-handler {:on-long-click (fn [_]
                                                 (reduce-handler "×"))})
   (u/op-button "-" op-handler {})])

(defn main-layout
  [landscape?]
  (let [disp (if landscape? u/display-element-landscape u/display-element)
        size (if landscape? (/ (u/screen-width) 20) (/ (u/screen-height) 10))]
    (concat
     [:linear-layout {:orientation :vertical}
      (disp ::w {:on-long-click (fn [_] (show-stack!))})
      (disp ::x {:on-long-click (fn [_] (show-stack!))})
      (disp ::y {:on-long-click (fn [_] (show-stack!))})
      [:linear-layout {:orientation :vertical
                       :layout-width :fill
                       :layout-height [size :dip]}
       [:edit-text {:id ::z
                    :input-type 0
                    :gravity :bottom
                    :single-line true
                    :max-lines 1
                    :layout-height :wrap-content
                    :typeface android.graphics.Typeface/MONOSPACE
                    :layout-width :fill
                    :on-long-click (fn [_] (toggle-edit-input))}]]]
     [[:linear-layout u/row-attributes
       (u/op-button "CLEAR" clear-handler {})
       (u/op-button "BACK" backspace-handler {})
       (u/op-button "±" invert-handler {})
       (u/op-button "^" op-handler {})]]
     (map (fn [i]
            (concat
             [:linear-layout u/row-attributes]
             (map (fn [j]
                    (let [n (+ (* i 3) j)]
                      (u/number-button n num-handler)))
                  (range 1 4))
             [(get op-column i)]))
          (range 3))
     [[:linear-layout u/row-attributes
       (u/op-button "RET" return-handler {})
       (u/number-button 0 num-handler)
       (u/number-button "." num-handler)
       (u/op-button "+" op-handler {:on-long-click (fn [_]
                                                     (reduce-handler "+"))})]])))

;; Activities ===================================================================
(defactivity warreq.kea.calc.Calculator
  :key :main
  :features [:no-title]
  (onCreate [this bundle]
            (.superOnCreate this bundle)
            (when (not= nil bundle)
              (let [s (map bigdec (.getStringArrayList bundle "stack"))]
                (reset! stack s)))
            (neko.debug/keep-screen-on this)
            (let [landscape? (= (get-screen-orientation) :landscape)]
              (on-ui
               (set-content-view! (*a) (main-layout landscape?)))
              (let [^TextView z (find-view (*a) ::z)
                    ^TextView y (find-view (*a) ::y)
                    ^TextView x (find-view (*a) ::x)
                    ^TextView w (find-view (*a) ::w)
                    size (if landscape?
                           (/ (u/screen-height) 25)
                           (/ (u/screen-height) 15))]
                (-> (AutofitHelper/create z)
                    (.setMinTextSize 10.0)
                    (.setMaxTextSize size))
                (add-watch stack :stack
                           (fn [key atom old new]
                             (.setText y (str (first new)))
                             (.setText x (str (second new)))
                             (.setText w (str (nth new 2 ""))))))))
  (onSaveInstanceState [this bundle]
                       (let [s (java.util.ArrayList. (map str (deref stack)))]
                         (.putStringArrayList bundle "stack" s))
                       (.superOnSaveInstanceState this bundle))
  (onResume [this]
            (.superOnResume this)
            ;; Force an event to make the watchers update
            (on-ui (reset! stack (deref stack)))))

(ns avi.t-scan
  (:require [midje.sweet :refer :all]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.generators :as gen']
            [clojure.test.check.properties :as prop]
            [com.gfredericks.test.chuck.properties :as prop']
            [clojure.test.check.clojure-test :refer [defspec]]
            [avi.buffer.locations :as l]
            [avi.scan :as scan]))

(def line-generator (gen/such-that #(= -1 (.indexOf % "\n")) gen/string-ascii))
(def lines-generator (gen/such-that #(not (zero? (count %))) (gen/vector line-generator)))
(def lines-and-position-generator
  (gen'/for [lines lines-generator
             i-base gen/pos-int
             :let [i (mod i-base (count lines))]
             j-base gen/pos-int
             :let [j (mod j-base (inc (count (get lines i))))]]
    {:lines lines
     :position [i j]}))

(defspec retreat-from-0-0-is-always-nil 100
  (prop/for-all [lines lines-generator]
    (nil? (scan/retreat [0 0] (scan/line-length lines)))))

(defspec advance-at-eof-is-always-nil 100
  (prop'/for-all [lines lines-generator
                  :let [i (dec (count lines))
                        j (count (last lines))]]
    (nil? (scan/advance [i j] (line-length lines)))))

(defspec retreat-position-always-decreases 100
  (prop/for-all [{:keys [lines position]} lines-and-position-generator]
    (or (nil? (scan/retreat position (scan/line-length lines)))
        (l/location< (scan/retreat position (scan/line-length lines)) position))))

(defspec advance-position-always-increases 100
  (prop/for-all [{:keys [lines position]} lines-and-position-generator]
    (or (nil? (scan/advance position (line-length lines)))
        (l/location< position (scan/advance position (line-length lines))))))

(defspec retreat-at-beginning-of-line-goes-to-newline-position 100
  (prop'/for-all [lines lines-generator
                  :when (>= (count lines) 2)
                  i (gen'/bounded-int 1 (dec (count lines)))]
    (= (scan/retreat [i 0] (scan/line-length lines))
       [(dec i) (count (get lines (dec i)))])))

(defspec advance-on-last-character-of-any-line-but-last-goes-to-newline-position 100
  (prop'/for-all [lines lines-generator
                  :when (>= (count lines) 2)
                  i (gen'/bounded-int 0 (- (count lines) 2))
                  :let [j (dec (count (get lines i)))]]
    (= (scan/advance [i j] (line-length lines)) [i (inc j)])))

(defspec retreat-never-skips-a-line 100
  (prop/for-all [{lines :lines [i j] :position} lines-and-position-generator]
    (or (nil? (scan/retreat [i j] (scan/line-length lines)))
        (= i (first (scan/retreat [i j] (scan/line-length lines))))
        (= (dec i) (first (scan/retreat [i j] (scan/line-length lines)))))))

(defspec advance-never-skips-a-line 100
  (prop/for-all [{lines :lines [i j] :position} lines-and-position-generator]
    (or (nil? (scan/advance [i j] (line-length lines)))
        (= i (first (scan/advance [i j] (line-length lines))))
        (= (inc i) (first (scan/advance [i j] (line-length lines)))))))

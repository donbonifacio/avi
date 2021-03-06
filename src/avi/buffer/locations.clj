(ns avi.buffer.locations
  (:refer-clojure :exclude [replace])
  (:require [schema.core :as s]))

(def ZLineNumber (s/constrained s/Int (complement neg?)))
(def ColumnNumber (s/constrained s/Int (complement neg?)))

(def Location
  [(s/one ZLineNumber "line number (zero-based)")
   (s/one ColumnNumber "column")])

(def AdjustmentBias
  (s/enum :left :right))

(s/defn location<
  [a :- Location
   b :- Location]
  (< (.compareTo a b) 0))

(s/defn location<=
  [a :- Location
   b :- Location]
  (<= (.compareTo a b) 0))

(s/defn location>
  [a :- Location
   b :- Location]
  (> (.compareTo a b) 0))

(s/defn location>=
  [a :- Location
   b :- Location]
  (>= (.compareTo a b) 0))

(s/defn advance :- (s/maybe Location)
  [[i j] :- Location
   line-length]
  (cond
    (>= j (line-length i))
    (if-not (line-length (inc i))
      nil
      [(inc i) 0])

    :else
    [i (inc j)]))

(s/defn retreat :- (s/maybe Location)
  [[i j] :- Location
   line-length]
  (cond
    (= [i j] [0 0])
    nil

    (>= j 1)
    [i (dec j)]

    :else
    [(dec i) (line-length (dec i))]))

(defn forward
  [pos line-length]
  (lazy-seq
    (when pos
      (cons pos (forward (advance pos line-length) line-length)))))

(defn backward
  [pos line-length]
  (lazy-seq
    (when pos
      (cons pos (backward (retreat pos line-length) line-length)))))

(s/defn forget-location? :- s/Bool
  [a :- Location
   b :- Location
   l :- Location]
  (and (location< a l)
       (location< l b)))

(defn line-count
  [text]
  (reduce (fn [n c]
            (cond-> n
              (= c \newline)
              inc))
          0
          text))

(defn last-line-length
  [text]
  (let [last-newline (.lastIndexOf text (int \newline))]
    (cond-> (count text)
      (not= last-newline -1)
      (- (inc last-newline)))))

(s/defn adjust-for-replacement :- (s/maybe Location)
  [[li lj :as l] :- Location
   [ai aj :as a] :- Location
   [bi bj :as b] :- Location
   text :- s/Str
   bias :- AdjustmentBias]
  (cond
    (and (= a b l) (= bias :left))
    l

    (forget-location? a b l)
    nil

    (location<= b l)
    (let [replacement-line-count (line-count text)]
      [(-> li
           (- (- bi ai))
           (+ replacement-line-count))
       (if (= li bi)
         (cond-> (+ (- lj bj) (last-line-length text))
           (zero? replacement-line-count)
           (+ aj)) 
         lj)])

    :else
    l))

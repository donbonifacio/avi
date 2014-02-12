(ns viv.core
  (:require [lanterna.screen :as lanterna]
            [clojure.string :as string])
  (:gen-class))

(defn start
  [[lines columns] filename]
  {:mode :normal
   :buffer {:name filename,
            :lines (string/split (slurp filename) #"\n"),
            :cursor [0 0],
            :last-explicit-j 0}
   :lines lines
   :columns columns
   :count nil
   :beep? false})

(defn- valid-column?
  [editor [i j]]
  (and (>= j 0)
       (< j (count (get-in editor [:buffer :lines i])))))

(defn- change-column
  [editor j-fn]
  (let [[i j] (get-in editor [:buffer :cursor])
        j (j-fn j)
        new-position [i j]]
    (if (valid-column? editor new-position)
      (-> editor
          (assoc-in [:buffer :cursor] new-position)
          (assoc-in [:buffer :last-explicit-j] j))
      (assoc editor :beep? true))))

(defn- valid-line?
  [editor i]
  (or (< i 0)
      (>= i (count (get-in editor [:buffer :lines])))))

(defn- j-within-line
  [editor [i j]]
  (let [j (get-in editor [:buffer :last-explicit-j])
        line-length (count (get-in editor [:buffer :lines i]))
        j-not-after-end (min (dec line-length) j)
        j-within-line (max 0 j-not-after-end)]
    j-within-line))

(defn- change-line
  [editor i-fn]
  (let [[i j] (get-in editor [:buffer :cursor])
        i (i-fn i)
        j (j-within-line editor [i j])]
    (if (valid-line? editor i)
      (assoc editor :beep? true)
      (assoc-in editor [:buffer :cursor] [i j]))))

(defn- move-to-end-of-line
  [editor]
  (let [[i j] (get-in editor [:buffer :cursor])
        line-length (count (get-in editor [:buffer :lines i]))
        j (max 0 (dec line-length))]
    (change-column editor (constantly j))))

(defn- update-count
  [editor digit]
  (assoc editor :count digit))

(def ^:private key-map
  {:enter #(assoc % :mode :finished)
   \0 #(change-column % (constantly 0))
   \2 #(update-count % 2)
   \3 #(update-count % 3)
   \4 #(update-count % 4)
   \5 #(update-count % 5)
   \6 #(update-count % 6)
   \7 #(update-count % 7)
   \8 #(update-count % 8)
   \9 #(update-count % 9)
   \$ move-to-end-of-line
   \h #(change-column % dec),
   \j #(change-line % inc),
   \k #(change-line % dec),
   \l #(change-column % inc)})

(defn- beep
  [editor]
  (assoc editor :beep? true))

(defn- key-handler
  [editor key]
  (or (get key-map key)
      beep))

(defn process
  [editor key]
  (let [repeat-count (or (:count editor) 1)
        handler (key-handler editor key)
        editor (assoc editor :beep? false)]
    (nth (iterate handler editor) repeat-count)))

(defn render
  [editor]
  (let [buffer-lines (map #(vector :white :black %) (get-in editor [:buffer :lines]))
        tilde-lines (repeat [:blue :black "~"])
        status-line [:black :white (get-in editor [:buffer :name])]]
    {:lines (vec
              (concat
                (take (- (:lines editor) 2) (concat buffer-lines tilde-lines))
                [status-line])),
     :cursor (get-in editor [:buffer :cursor])}))

(defn- update-screen
  [editor screen]
  (let [rendition (render editor)
        screen-lines (:lines rendition)
        [cursor-i cursor-j] (:cursor rendition)]
    (doseq [i (range (count screen-lines))]
      (let [[color background text] (get screen-lines i)]
        (lanterna/put-string screen 0 i text {:bg background, :fg color})))
    (lanterna/move-cursor screen cursor-j cursor-i)
    (lanterna/redraw screen)))

(defn -main
  [filename]
  (let [screen (lanterna/get-screen :unix)]
    (lanterna/start screen)
    (loop [editor (let [[columns lines] (lanterna/get-size screen)]
                    (start [lines columns] filename))]
      (update-screen editor screen)
      (if-not (= (:mode editor) :finished)
        (recur (process editor (lanterna/get-key-blocking screen)))))
    (lanterna/stop screen)))

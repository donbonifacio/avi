(ns avi.mode.insert
  (:require [packthread.core :refer :all]
            [packthread.lenses :as l]
            [avi.beep :as beep]
            [avi.editor :as e]
            [avi.buffer :as b]
            [avi.pervasive :refer :all]))

(defn enter-insert-mode
  [editor spec & [script]]
  (+> editor
      (assoc :mode :insert,
             :message [:white :black "--INSERT--"]
             :insert-mode-state {:count (or (:count spec) 1)
                                 :script (or script [])})
      (in e/current-buffer
          b/start-transaction)))

(defn advance-for-append
  [{[i j] :point, lines :lines :as buffer}]
  (assoc buffer :point [i (min (count (get lines i)) (inc j))]))

(defn move-to-eol
  [{[i] :point, lines :lines :as buffer}]
  (assoc buffer :point [i (count (get lines i))]))

(def mappings-which-enter-insert-mode
  {"a" ^:no-repeat (fn+> [editor spec]
                     (enter-insert-mode spec)
                     (in e/current-buffer
                       advance-for-append))

   "i" ^:no-repeat (fn+> [editor spec]
                     (enter-insert-mode spec))

   "o" ^:no-repeat (fn+> [editor spec]
                     (let [{:keys [lines] [i] :point} (e/current-buffer editor)
                           eol (count (get lines i))]
                       (enter-insert-mode spec [[:keystroke "<Enter>"]])
                       (in e/current-buffer
                           move-to-eol
                           (b/change [i eol] [i eol] "\n" :right))))

   "A" ^:no-repeat (fn+> [editor spec]
                     (enter-insert-mode spec)
                     (in e/current-buffer
                         move-to-eol))

   "O" ^:no-repeat (fn+> [editor spec]
                     (let [{[i] :point} (e/current-buffer editor)]
                       (enter-insert-mode spec [[:keystroke "<Enter>"]])
                       (in e/current-buffer
                           (b/change [i 0] [i 0] "\n" :left)
                           (b/operate {:operator :move-point
                                       :motion [:goto [i 0]]}))))})

(defn- key->text
  [key]
  (if (= key "<Enter>")
    "\n"
    key))

(defn- update-buffer-for-insert-event
  [editor [event-type event-data :as event]]
  (+> editor
    (if-not (= event-type :keystroke)
      beep/beep
      (in e/current-buffer
          (if (= event-data "<BS>")
            (if (= [0 0] (:point (e/current-buffer editor)))
              beep/beep
              b/backspace)
            (b/insert-text (key->text event-data)))))))

(defn- play-script
  [editor script]
  (reduce
    update-buffer-for-insert-event
    editor
    script))

(defn- play-script-repeat-count-times
  [editor]
  (let [{script :script,
         repeat-count :count} (:insert-mode-state editor)]
    (reduce
      (fn [editor n]
        (play-script editor script))
      editor
      (range (dec repeat-count)))))

(def wrap-handle-escape
  (e/keystroke-middleware "<Esc>"
    (fn+> [editor]
      play-script-repeat-count-times
      (dissoc :insert-mode-state)
      (let [b (e/current-buffer editor)
            [i j] (:point b)
            new-j (max (dec j) 0)]
        (in e/current-buffer
            (b/operate {:operator :move-point
                        :motion [:goto [i new-j]]})
            b/commit))
      (e/enter-normal-mode))))

(defn- wrap-record-event
  [responder]
  (fn+> [editor event]
    (responder event)
    (update-in [:insert-mode-state :script] conj event)))

(def responder
  (-> update-buffer-for-insert-event
      wrap-record-event
      wrap-handle-escape))

(def wrap-mode (e/mode-middleware :insert responder))

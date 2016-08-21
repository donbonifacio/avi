(ns avi.t-panes
  (:require [midje.sweet :refer :all]
            [avi.panes :as p]))

(facts "about finding panes to render"
  (fact "a single root pane fills the screen"
    (p/panes-to-render {:viewport {:size [10 8]}
                        ::p/tree {::p/lens 0}})
      => [{::p/lens 0
           ::p/shape [[0 0] [9 8]]}]
    (p/panes-to-render {:viewport {:size [8 17]}
                        ::p/tree {::p/lens 6}})
      => [{::p/lens 6
           ::p/shape [[0 0] [7 17]]}])
  (fact "a single horizonal split works correctly"
    (p/panes-to-render {:viewport {:size [10 8]}
                        ::p/tree {::p/subtrees [{::p/lens 0 ::p/extent 3}
                                                {::p/lens 1}]}})
      => [{::p/lens 0
           ::p/shape [[0 0] [3 8]]}
          {::p/lens 1
           ::p/shape [[3 0] [6 8]]}])
  (fact "two horizontal splits work correctly"
    (p/panes-to-render {:viewport {:size [10 8]}
                        ::p/tree {::p/subtrees [{::p/lens 0 ::p/extent 2}
                                                {::p/lens 1 ::p/extent 2}
                                                {::p/lens 2}]}})
      => [{::p/lens 0
           ::p/shape [[0 0] [2 8]]}
          {::p/lens 1
           ::p/shape [[2 0] [2 8]]}
          {::p/lens 2
           ::p/shape [[4 0] [5 8]]}]))

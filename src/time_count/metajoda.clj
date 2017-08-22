(ns time-count.metajoda
  (:require [time-count.core :refer [SequenceTime]]
            [time-count.iso8601 :refer :all]
            [time-count.meta-joda :refer [scale-to-Period]]
            [time-count.time-count :refer [nested-first nested-last]]
            [time-count.allens-interval-algebra :refer [relation-mj]])

  (:import [org.joda.time DateTime]
           [org.joda.time.format DateTimeFormat]))

;; TODO Replace meta-joda require with new stuff in core
;; TODO Replace time-count require with new stuff here.
;; TODO Replace allens-interval-algebra require with new stuff somewhere.
;; TODO Add namespaced keywords to core.


(defrecord MetaJodaTime [^DateTime dt nesting]

  SequenceTime

  (next-interval [t]
    (MetaJodaTime.
      (.plus dt (scale-to-Period (first nesting)))
      nesting))

  (prev-interval [t]
    (MetaJodaTime.
      (.minus dt (scale-to-Period (first nesting)))
      nesting))

  (nest [t scale]
    (let [mjt (cons dt nesting)
          [dtf & new-nesting] (nested-first scale mjt)
          [dtl & _] (nested-last scale mjt)]

      {:starts   (MetaJodaTime. dtf new-nesting)
       :finishes (MetaJodaTime. dtl new-nesting)}))

  (enclosing-immediate [t]
    (let [[dtf & _] (nested-first (first nesting) (cons dt (rest nesting)))]
      (MetaJodaTime. dtf (rest nesting))))

  (relation [t1 t2]
    (relation-mj
      (cons (:dt t1) (:nesting t1))
      (cons (:dt t2) (:nesting t2))))


  ISO8601Mappable

  (to-iso [t]
    (.print (-> nesting nesting-to-pattern DateTimeFormat/forPattern .withOffsetParsed) dt)))



(extend-type String
  ISO8601Pattern
  (iso-parser [pattern]
    (fn [time-string]
      (MetaJodaTime.
        (.parseDateTime (-> pattern DateTimeFormat/forPattern .withOffsetParsed) time-string)
        (pattern-to-nesting pattern)))))


(extend-type String
  ISO8601SequenceTime
  (from-iso-sequence-time [time-string]
    ((-> time-string time-string-pattern iso-parser) time-string)))
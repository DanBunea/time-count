(ns time-count.demo
  (:require
    [time-count.core :refer :all]
    [time-count.iso8601 :refer [to-iso from-iso t->]]
    [time-count.metajoda :refer [->MetaJodaTime]]
  ;  [time-count.meta-joda :refer [place-value to-nesting]]
    [midje.sweet :refer :all])
  (:import [org.joda.time DateTime]))


;; Don't think of measuring time, think of a complicated counting system.
;; A calendar is a sequence of intervals of scale "day"
;; A sequence of days can be nested inside a sequence of months, within a sequence of years.
;; Or a sequence of days can be nested inside a sequence of quarters within a sequence of years.
;; Or days within years or days within weeks.
;; In a sequence of months, the scale is month. It doesn't matter that not all are the same length. They are all a month!
;; If you nest days (named with numbers) within a month, then some februaries have 28 and some have 29.
;; String representation should be seamless (using ISO 8601 where possible)



;; Here's a different model:
;;  Calendars and time are (for most business software) weird ways of *counting*, not *measuring*.
;;  All times are intervals, in sequences (so we can count forward and backward through them).
;;  A sequence of intervals can be nested within an interval of a larger "scale".
;;  E.g. Days are nested within months. Months are nested within years.
;;  Relations between intervals can be well defined.

;; String representation: ISO 8601
;;  Having a string representation of a time is important.
;;  time-count uses ISO 8601, whenever there is a suitable representation,
;;  and has operations to go between the string and the representation used in computations.

;; MetaJoda (for computation)
;;  Most of the time, that computation-friendly representation is 'meta-joda',
;;  which just adds some metadata to a JodaTime DateTime.
;;  The metadata represents a nesting of scales intended to be significant.
;;
;;  This is a place-holder implementation. It could be any representation that supports
;;  a few primitives (described below). For now, this lets us use a lot of work the JodaTime people have done!
;;  E.g. How many days are in February in 2017? When does New York switch to Daylight Savings Time in 2017?
;;


(fact "Time representation needs metadata representing nested scale"
      (-> "2017-04-09" from-iso) => (->MetaJodaTime (DateTime. 2017 4 9 0 0 0 0) [:day :month :year])
      (to-iso (->MetaJodaTime (DateTime. 2017 4 9 0 0 0 0) [:day :month :year])) => "2017-04-09"
      (-> "2017-04-09T11:17" from-iso :nesting) => [:minute :hour :day :month :year]
      (-> "2017-04" from-iso :nesting) => [:month :year])


(fact "A convenience macro allows application of time transforming functions with ISO 8601 strings."
      ;; This may be mostly for tests and demos. Perhaps it will be used in some apps for data interchange.
      (t-> "2017-04-30" identity) => "2017-04-30"
      ;     (t->> "2017-04-30" identity) => "2017-04-30")
      )

;;Treating all times as intervals has some implications.
(fact "An interval is part of a sequence, so next is meaningful"
      (t-> "2017-04-09" next-interval) => "2017-04-10"
      (t-> "2017-04" next-interval) => "2017-05"
      (t-> "2017" next-interval) => "2018"
      (t-> "2017-04-09T11:17" next-interval) => "2017-04-09T11:18"
      (t-> "2017-02-28" next-interval) => "2017-03-01"
      (t-> "2016-02-28" next-interval) => "2016-02-29"
      (t-> "2017-070" next-interval) => "2017-071"
      (t-> "2017-365" next-interval) => "2018-001"
      (t-> "2017-W52" next-interval) => "2018-W01")

(fact "A sequence can be nested within an interval of a larger scale"
      (t-> "2017-04" (nest :day) t-sequence count) => 30
      (t-> "2017" (nest :day) t-sequence count) => 365
      (t-> "2016" (nest :day) t-sequence count) => 366
      ; :week-year is still a puzzle (t-> "2017" (nest :week)) t-sequence count) => 52?
      (t-> "2017" (nest :month) t-sequence count) => 12
      (t-> "2017" (nest :month) t-sequence first) => "2017-01"
      (t-> "2017" (nest :month) t-sequence second) => "2017-02"
      (t-> "2017" (nest :month) t-sequence last) => "2017-12")

(fact "a member of an interval sequence nested within a larger interval"
      (t-> "2017-04-09" enclosing-immediate) => "2017-04")


(facts "about composing higher-level time operations from the basic protocol."
       (let [later (fn [n] #(-> {:starts %} t-sequence (nth n)))]
         (t-> "2017-04-19" ((later 5))) => "2017-04-24"
         (t-> "2017-04" ((later 5))) => "2017-09"
         (t-> "2017" ((later 5))) => "2022")

       (let [last-day #(-> % (nest :day) t-sequence last)
             last-day2 #(-> % (nest :day) :finishes)]
         (t-> "2017-04" last-day) => "2017-04-30"
         (t-> "2017" last-day) => "2017-365"
         (t-> "2017-04" last-day2) => "2017-04-30")

       (let [last-day #(-> % (nest :day) t-sequence last)
             eom #(-> % (enclosing :month) last-day)]
         (t-> "2017-04-19" eom) => "2017-04-30"
         (t-> "2017-04" eom) => "2017-04-30"
         (t-> "2017-04-19T15:12" eom) => "2017-04-30"))
; A more complex eom could preserve nesting
; and find last interval of same scale, etc.


(comment


  ; Business rules can be composed from these basic operations.
  (fact " Example: invoice due"
        (let [net-30 (comp #(nth % 30) interval-seq)
              eom (comp (nested-last :day) (enclosing-immediate :month))
              net-30-EOM (comp eom next-interval (enclosing-immediate :month))
              overdue? (fn [terms completion-date today] (#{:after :met-by} (relation-str today (t-> completion-date terms))))]

          (t-> "2017-01-15" net-30) => "2017-02-14"
          (t-> "2017-01-15" net-30-EOM) => "2017-02-28"
          (overdue? net-30 "2017-01-15" "2017-02-10T14:30") => falsey
          (overdue? net-30 "2017-01-15" "2017-02-20") => truthy
          (overdue? net-30-EOM "2017-01-15" "2017-02-20") => falsey
          (overdue? net-30-EOM "2017-01-15" "2017-03-01") => truthy))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; Building up functions and then deriving holidays ;;
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  (defn day-of-week [ymd]
    (-> ymd
        ((to-nesting [:day :week :week-year]))
        (#(place-value :day %))))

  (defn thursday? [ymd]
    (= 4 (day-of-week ymd)))

  (defn november [year]
    (-> year
        ((nested-seq :month))
        (nth 10)))

  (defn thanksgiving-us [year]
    (-> year
        november
        ((nested-seq :day))
        (#(filter thursday? %))
        (nth 3)))

  (fact "US Thanksgiving is 4th Thursday in November"
        (t-> "2017" thanksgiving-us) => "2017-11-23"
        (t-> "2018" thanksgiving-us) => "2018-11-22"
        (t->> "2017" interval-seq
              (map thanksgiving-us)
              (take 2))
        => ["2017-11-23" "2018-11-22"])

  ;;;;

  (defn monday? [ymd]
    (= 1 (day-of-week ymd)))

  (defn may [year]
    (-> year
        ((nested-seq :month))
        (nth 4)))

  (defn memorial-day-us [year]
    (-> year
        may
        ((nested-seq :day))
        (#(filter monday? %))
        last))

  (fact "US Memorial Day is the last Monday in May"
        (t-> "2017" memorial-day-us) => "2017-05-29"
        (t-> "2018" memorial-day-us) => "2018-05-28")



  )

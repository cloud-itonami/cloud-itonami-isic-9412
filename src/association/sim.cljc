(ns association.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean member through
  intake -> jurisdiction assessment -> ethics-complaint screening ->
  certification-issuance proposal (always escalates) -> human approval
  -> commit, then through disciplinary-referral-finalization proposal
  (always escalates) -> human approval -> commit, then shows five HARD
  holds (a jurisdiction with no spec-basis, insufficient continuing-
  education hours, an unresolved ethics complaint screened directly
  via `:complaint/screen` [never via an actuation op against an
  unscreened member -- see this actor's own governor ns docstring /
  the lesson `parksafety`'s ADR-2607071922 Decision 5, `eldercare`'s,
  `museum`'s, `conservation`'s, `salon`'s, `entertainment`'s,
  `casework`'s, `hospital`'s, `facility`'s and `school`'s ADR-0001s
  already recorded], and a double certification-issuance/disciplinary-
  referral-finalization of an already-processed member) that never
  reach a human at all, and prints the audit ledger + the draft
  certification-issuance and disciplinary-referral-finalization
  records."
  (:require [langgraph.graph :as g]
            [association.store :as store]
            [association.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :association-officer :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== member/intake member-1 (JPN, clean; 40/30 CE hours, no unresolved complaint) ==")
    (println (exec! actor "t1" {:op :member/intake :subject "member-1"
                                :patch {:id "member-1" :member-name "Sakura Tanaka"}} operator))

    (println "== jurisdiction/assess member-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :jurisdiction/assess :subject "member-1"} operator))
    (println (approve! actor "t2"))

    (println "== complaint/screen member-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :complaint/screen :subject "member-1"} operator))
    (println (approve! actor "t3"))

    (println "== certification/issue member-1 (always escalates -- actuation/issue-certification) ==")
    (let [r (exec! actor "t4" {:op :certification/issue :subject "member-1"} operator)]
      (println r)
      (println "-- human officer approves --")
      (println (approve! actor "t4")))

    (println "== discipline/finalize member-1 (always escalates -- actuation/finalize-disciplinary-referral) ==")
    (let [r (exec! actor "t5" {:op :discipline/finalize :subject "member-1"} operator)]
      (println r)
      (println "-- human officer approves --")
      (println (approve! actor "t5")))

    (println "== jurisdiction/assess member-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :jurisdiction/assess :subject "member-2" :no-spec? true} operator))

    (println "== jurisdiction/assess member-3 (escalates -- human approves; sets up the CE-hours test) ==")
    (println (exec! actor "t7" {:op :jurisdiction/assess :subject "member-3"} operator))
    (println (approve! actor "t7"))

    (println "== certification/issue member-3 (20/30 CE hours -> HARD hold) ==")
    (println (exec! actor "t8" {:op :certification/issue :subject "member-3"} operator))

    (println "== complaint/screen member-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :complaint/screen :subject "member-4"} operator))

    (println "== certification/issue member-1 AGAIN (double-certification -> HARD hold) ==")
    (println (exec! actor "t10" {:op :certification/issue :subject "member-1"} operator))

    (println "== discipline/finalize member-1 AGAIN (double-referral -> HARD hold) ==")
    (println (exec! actor "t11" {:op :discipline/finalize :subject "member-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft certification-issuance records ==")
    (doseq [r (store/certification-history db)] (println r))

    (println "== draft disciplinary-referral-finalization records ==")
    (doseq [r (store/discipline-history db)] (println r))))

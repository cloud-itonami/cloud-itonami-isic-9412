(ns association.governor-contract-test
  "The governor contract as executable tests -- the association analog
  of `cloud-itonami-isic-6512`'s `casualty.governor-contract-test`.
  The single invariant under test:

    AssocOps-LLM never issues a certification or finalizes a
    disciplinary referral the Association Governance Governor would
    reject, `:certification/issue`/`:discipline/finalize` NEVER
    auto-commit at any phase, `:member/intake` (no direct capital
    risk) MAY auto-commit when clean, and every decision (commit OR
    hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [association.store :as store]
            [association.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :association-officer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  "Walks `subject` through assess -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :jurisdiction/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(defn- screen!
  "Walks `subject` through ethics-complaint screening -> approve,
  leaving a screening on file. Only safe to call for a member whose
  complaint status has already resolved -- an unresolved complaint
  HARD-holds the screen itself (see
  `complaint-unresolved-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :complaint/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :member/intake :subject "member-1"
                   :patch {:id "member-1" :member-name "Sakura Tanaka"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Tanaka" (:member-name (store/member db "member-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "member-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "member-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "member-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "member-1")) "no assessment written"))))

(deftest certification-issue-without-assessment-is-held
  (testing "certification/issue before any jurisdiction assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :certification/issue :subject "member-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest continuing-education-insufficient-is-held
  (testing "a member whose completed CE hours fall short of the required minimum -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t5pre" "member-3")
          res (exec-op actor "t5" {:op :certification/issue :subject "member-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:continuing-education-insufficient} (-> (store/ledger db) last :basis)))
      (is (empty? (store/certification-history db))))))

(deftest complaint-unresolved-is-held-and-unoverridable
  (testing "an unresolved ethics complaint on a member -> HOLD, and never reaches request-approval -- exercised via :complaint/screen DIRECTLY, not via the actuation op against an unscreened member (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's and school's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :complaint/screen :subject "member-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:complaint-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/complaint-of db "member-4")) "no clearance written"))))

(deftest certification-issue-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, CE-sufficient student still ALWAYS interrupts for human approval -- actuation/issue-certification is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t7pre" "member-1")
          r1 (exec-op actor "t7" {:op :certification/issue :subject "member-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, certification record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:certified? (store/member db "member-1"))))
          (is (= 1 (count (store/certification-history db))) "one draft certification record"))))))

(deftest discipline-finalize-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, resolved-complaint member still ALWAYS interrupts for human approval -- actuation/finalize-disciplinary-referral is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "member-1")
          _ (screen! actor "t8pre2" "member-1")
          r1 (exec-op actor "t8" {:op :discipline/finalize :subject "member-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, disciplinary-referral record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:disciplined? (store/member db "member-1"))))
          (is (= 1 (count (store/discipline-history db))) "one draft disciplinary-referral record"))))))

(deftest certification-issue-double-certification-is-held
  (testing "issuing the same member's certification twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t9pre" "member-1")
          _ (exec-op actor "t9a" {:op :certification/issue :subject "member-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :certification/issue :subject "member-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-certified} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/certification-history db))) "still only the one earlier certification"))))

(deftest discipline-finalize-double-referral-is-held
  (testing "finalizing the same member's disciplinary referral twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t10pre" "member-1")
          _ (screen! actor "t10pre2" "member-1")
          _ (exec-op actor "t10a" {:op :discipline/finalize :subject "member-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :discipline/finalize :subject "member-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-disciplined} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/discipline-history db))) "still only the one earlier disciplinary referral"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :member/intake :subject "member-1"
                          :patch {:id "member-1" :member-name "Sakura Tanaka"}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "member-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))

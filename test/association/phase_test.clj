(ns association.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:certification/issue`/`:discipline/finalize` must
  NEVER be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [association.phase :as phase]))

(deftest certification-issue-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real certification issuance"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :certification/issue))
          (str "phase " n " must not auto-commit :certification/issue")))))

(deftest discipline-finalize-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real disciplinary-referral finalization"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :discipline/finalize))
          (str "phase " n " must not auto-commit :discipline/finalize")))))

(deftest complaint-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :complaint/screen))
          (str "phase " n " must not auto-commit :complaint/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":member/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:member/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :member/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :certification/issue} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :discipline/finalize} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :member/intake} :commit)))))

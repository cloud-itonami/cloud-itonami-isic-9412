(ns association.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [association.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Tanaka" (:member-name (store/member s "member-1"))))
      (is (= "JPN" (:jurisdiction (store/member s "member-1"))))
      (is (= 40 (:ce-hours-completed (store/member s "member-1"))))
      (is (= 30 (:ce-hours-required (store/member s "member-1"))))
      (is (false? (:complaint-unresolved? (store/member s "member-1"))))
      (is (= 20 (:ce-hours-completed (store/member s "member-3"))))
      (is (true? (:complaint-unresolved? (store/member s "member-4"))))
      (is (false? (:certified? (store/member s "member-1"))))
      (is (false? (:disciplined? (store/member s "member-1"))))
      (is (= ["member-1" "member-2" "member-3" "member-4"]
             (mapv :id (store/all-members s))))
      (is (nil? (store/complaint-of s "member-1")))
      (is (nil? (store/assessment-of s "member-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/certification-history s)))
      (is (= [] (store/discipline-history s)))
      (is (zero? (store/next-certification-sequence s "JPN")))
      (is (zero? (store/next-discipline-sequence s "JPN")))
      (is (false? (store/member-already-certified? s "member-1")))
      (is (false? (store/member-already-disciplined? s "member-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :member/upsert
                                 :value {:id "member-1" :member-name "Sakura Tanaka"}})
        (is (= "Sakura Tanaka" (:member-name (store/member s "member-1"))))
        (is (= 40 (:ce-hours-completed (store/member s "member-1"))) "unrelated field preserved"))
      (testing "assessment / complaint payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["member-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "member-1")))
        (store/commit-record! s {:effect :complaint/set :path ["member-1"]
                                 :payload {:member-id "member-1" :verdict :resolved}})
        (is (= {:member-id "member-1" :verdict :resolved} (store/complaint-of s "member-1"))))
      (testing "certification issuance drafts a certification record and advances the sequence"
        (store/commit-record! s {:effect :member/mark-certified :path ["member-1"]})
        (is (= "JPN-CRT-000000" (get (first (store/certification-history s)) "record_id")))
        (is (= "certification-issuance-draft" (get (first (store/certification-history s)) "kind")))
        (is (true? (:certified? (store/member s "member-1"))))
        (is (= 1 (count (store/certification-history s))))
        (is (= 1 (store/next-certification-sequence s "JPN")))
        (is (true? (store/member-already-certified? s "member-1")))
        (is (false? (store/member-already-certified? s "member-2"))))
      (testing "disciplinary-referral finalization drafts a record and advances the sequence"
        (store/commit-record! s {:effect :member/mark-disciplined :path ["member-1"]})
        (is (= "JPN-DSC-000000" (get (first (store/discipline-history s)) "record_id")))
        (is (= "disciplinary-referral-finalization-draft" (get (first (store/discipline-history s)) "kind")))
        (is (true? (:disciplined? (store/member s "member-1"))))
        (is (= 1 (count (store/discipline-history s))))
        (is (= 1 (store/next-discipline-sequence s "JPN")))
        (is (true? (store/member-already-disciplined? s "member-1")))
        (is (false? (store/member-already-disciplined? s "member-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/member s "nope")))
    (is (= [] (store/all-members s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/certification-history s)))
    (is (= [] (store/discipline-history s)))
    (is (zero? (store/next-certification-sequence s "JPN")))
    (is (zero? (store/next-discipline-sequence s "JPN")))
    (store/with-members s {"x" {:id "x" :member-name "n" :ce-hours-completed 40
                               :ce-hours-required 30 :complaint-unresolved? false
                               :certified? false :disciplined? false
                               :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:member-name (store/member s "x"))))))

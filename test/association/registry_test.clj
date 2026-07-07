(ns association.registry-test
  (:require [clojure.test :refer [deftest is]]
            [association.registry :as r]))

;; ----------------------------- continuing-education-hours-insufficient? -----------------------------

(deftest not-insufficient-when-at-or-above-required
  (is (not (r/continuing-education-hours-insufficient? {:ce-hours-completed 30 :ce-hours-required 30})))
  (is (not (r/continuing-education-hours-insufficient? {:ce-hours-completed 40 :ce-hours-required 30}))))

(deftest insufficient-when-below-required
  (is (r/continuing-education-hours-insufficient? {:ce-hours-completed 29 :ce-hours-required 30}))
  (is (r/continuing-education-hours-insufficient? {:ce-hours-completed 20 :ce-hours-required 30})))

(deftest insufficient-is-false-on-missing-fields
  (is (not (r/continuing-education-hours-insufficient? {})))
  (is (not (r/continuing-education-hours-insufficient? {:ce-hours-completed 20}))))

;; ----------------------------- register-certification-issuance -----------------------------

(deftest certification-issuance-is-a-draft-not-a-real-certification
  (let [result (r/register-certification-issuance "member-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest certification-issuance-assigns-certification-number
  (let [result (r/register-certification-issuance "member-1" "JPN" 7)]
    (is (= (get result "certification_number") "JPN-CRT-000007"))
    (is (= (get-in result ["record" "member_id"]) "member-1"))
    (is (= (get-in result ["record" "kind"]) "certification-issuance-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest certification-issuance-validation-rules
  (is (thrown? Exception (r/register-certification-issuance "" "JPN" 0)))
  (is (thrown? Exception (r/register-certification-issuance "member-1" "" 0)))
  (is (thrown? Exception (r/register-certification-issuance "member-1" "JPN" -1))))

;; ----------------------------- register-disciplinary-referral-finalization -----------------------------

(deftest disciplinary-referral-is-a-draft-not-a-real-referral
  (let [result (r/register-disciplinary-referral-finalization "member-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest disciplinary-referral-assigns-referral-number
  (let [result (r/register-disciplinary-referral-finalization "member-1" "JPN" 3)]
    (is (= (get result "referral_number") "JPN-DSC-000003"))
    (is (= (get-in result ["record" "member_id"]) "member-1"))
    (is (= (get-in result ["record" "kind"]) "disciplinary-referral-finalization-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest disciplinary-referral-validation-rules
  (is (thrown? Exception (r/register-disciplinary-referral-finalization "" "JPN" 0)))
  (is (thrown? Exception (r/register-disciplinary-referral-finalization "member-1" "" 0)))
  (is (thrown? Exception (r/register-disciplinary-referral-finalization "member-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-certification-issuance "member-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-certification-issuance "member-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-CRT-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-CRT-000001" (get-in hist2 [1 "record_id"])))))

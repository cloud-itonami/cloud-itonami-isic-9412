(ns association.facts-test
  (:require [clojure.test :refer [deftest is]]
            [association.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

(deftest nld-has-a-spec-basis
  (is (some? (facts/spec-basis "NLD")))
  (is (string? (:provenance (facts/spec-basis "NLD")))))

(deftest nld-required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "NLD")]
    (is (facts/required-evidence-satisfied? "NLD" all))
    (is (not (facts/required-evidence-satisfied? "NLD" (rest all))))))

(deftest coverage-includes-nld-alongside-all-others
  (let [report (facts/coverage ["JPN" "USA" "GBR" "DEU" "NLD"])]
    (is (= 5 (:covered report)))
    (is (= ["DEU" "GBR" "JPN" "NLD" "USA"] (:covered-jurisdictions report)))))

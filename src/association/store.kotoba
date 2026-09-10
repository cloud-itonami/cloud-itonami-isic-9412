(ns association.store
  "SSoT for the association actor, behind a `Store` protocol so the
  backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/association/store_contract_test.clj), which is the whole
  point: the actor, the Association Governance Governor and the audit
  ledger never know which SSoT they run on.

  Like `hospital.store`'s dual treatment/discharge history,
  `school.store`'s dual promotion/safeguarding-record history and
  every other dual-actuation sibling before it, this actor has TWO
  actuation events (issuing a certification, finalizing a
  disciplinary referral) acting on the SAME entity (a professional
  member), each with its OWN history collection, sequence counter and
  dedicated double-actuation-guard boolean (`:certified?`/
  `:disciplined?`, never a `:status` value) -- the same discipline
  every prior sibling governor's guards establish, informed by
  `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320).

  The ledger stays append-only on every backend: 'which member was
  screened for an unresolved ethics complaint, which certification was
  issued, which disciplinary referral was finalized, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a member trusting an association
  needs, and the evidence an operator needs if a certification or
  disciplinary referral is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [association.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (member [s id])
  (all-members [s])
  (complaint-of [s member-id] "committed ethics-complaint screening verdict for a member, or nil")
  (assessment-of [s member-id] "committed jurisdiction assessment, or nil")
  (ledger [s])
  (certification-history [s] "the append-only certification-issuance history (association.registry drafts)")
  (discipline-history [s] "the append-only disciplinary-referral-finalization history (association.registry drafts)")
  (next-certification-sequence [s jurisdiction] "next certification-issuance-number sequence for a jurisdiction")
  (next-discipline-sequence [s jurisdiction] "next disciplinary-referral-number sequence for a jurisdiction")
  (member-already-certified? [s member-id] "has this member's certification already been issued?")
  (member-already-disciplined? [s member-id] "has this member's disciplinary referral already been finalized?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-members [s members] "replace/seed the member directory (map id->member)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained member set covering both actuation
  lifecycles (issuing a certification, finalizing a disciplinary
  referral) so the actor + tests run offline."
  []
  {:members
   {"member-1" {:id "member-1" :member-name "Sakura Tanaka"
                :ce-hours-completed 40 :ce-hours-required 30 :complaint-unresolved? false
                :certified? false :disciplined? false
                :jurisdiction "JPN" :status :intake}
    "member-2" {:id "member-2" :member-name "Atlantis Doe"
                :ce-hours-completed 40 :ce-hours-required 30 :complaint-unresolved? false
                :certified? false :disciplined? false
                :jurisdiction "ATL" :status :intake}
    "member-3" {:id "member-3" :member-name "鈴木一郎"
                :ce-hours-completed 20 :ce-hours-required 30 :complaint-unresolved? false
                :certified? false :disciplined? false
                :jurisdiction "JPN" :status :intake}
    "member-4" {:id "member-4" :member-name "田中花子"
                :ce-hours-completed 40 :ce-hours-required 30 :complaint-unresolved? true
                :certified? false :disciplined? false
                :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- finalize-certification!
  "Backend-agnostic `:member/mark-certified` -- looks up the member
  via the protocol and drafts the certification-issuance record, and
  returns {:result .. :member-patch ..} for the caller to persist."
  [s member-id]
  (let [m (member s member-id)
        seq-n (next-certification-sequence s (:jurisdiction m))
        result (registry/register-certification-issuance member-id (:jurisdiction m) seq-n)]
    {:result result
     :member-patch {:certified? true
                   :certification-number (get result "certification_number")}}))

(defn- finalize-discipline!
  "Backend-agnostic `:member/mark-disciplined` -- looks up the member
  via the protocol and drafts the disciplinary-referral-finalization
  record, and returns {:result .. :member-patch ..} for the caller to
  persist."
  [s member-id]
  (let [m (member s member-id)
        seq-n (next-discipline-sequence s (:jurisdiction m))
        result (registry/register-disciplinary-referral-finalization member-id (:jurisdiction m) seq-n)]
    {:result result
     :member-patch {:disciplined? true
                   :referral-number (get result "referral_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (member [_ id] (get-in @a [:members id]))
  (all-members [_] (sort-by :id (vals (:members @a))))
  (complaint-of [_ id] (get-in @a [:complaints id]))
  (assessment-of [_ member-id] (get-in @a [:assessments member-id]))
  (ledger [_] (:ledger @a))
  (certification-history [_] (:certifications @a))
  (discipline-history [_] (:disciplines @a))
  (next-certification-sequence [_ jurisdiction] (get-in @a [:certification-sequences jurisdiction] 0))
  (next-discipline-sequence [_ jurisdiction] (get-in @a [:discipline-sequences jurisdiction] 0))
  (member-already-certified? [_ member-id] (boolean (get-in @a [:members member-id :certified?])))
  (member-already-disciplined? [_ member-id] (boolean (get-in @a [:members member-id :disciplined?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :member/upsert
      (swap! a update-in [:members (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :complaint/set
      (swap! a assoc-in [:complaints (first path)] payload)

      :member/mark-certified
      (let [member-id (first path)
            {:keys [result member-patch]} (finalize-certification! s member-id)
            jurisdiction (:jurisdiction (member s member-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:certification-sequences jurisdiction] (fnil inc 0))
                       (update-in [:members member-id] merge member-patch)
                       (update :certifications registry/append result))))
        result)

      :member/mark-disciplined
      (let [member-id (first path)
            {:keys [result member-patch]} (finalize-discipline! s member-id)
            jurisdiction (:jurisdiction (member s member-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:discipline-sequences jurisdiction] (fnil inc 0))
                       (update-in [:members member-id] merge member-patch)
                       (update :disciplines registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-members [s members] (when (seq members) (swap! a assoc :members members)) s))

(defn seed-db
  "A MemStore seeded with the demo member set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :complaints {} :ledger [] :certification-sequences {}
                           :certifications [] :discipline-sequences {} :disciplines []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment/complaint payloads, ledger facts,
  certification/discipline records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:member/id                        {:db/unique :db.unique/identity}
   :assessment/member-id             {:db/unique :db.unique/identity}
   :complaint/member-id              {:db/unique :db.unique/identity}
   :ledger/seq                       {:db/unique :db.unique/identity}
   :certification/seq                {:db/unique :db.unique/identity}
   :discipline/seq                   {:db/unique :db.unique/identity}
   :certification-sequence/jurisdiction {:db/unique :db.unique/identity}
   :discipline-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- member->tx [{:keys [id member-name ce-hours-completed ce-hours-required complaint-unresolved?
                          certified? disciplined?
                          jurisdiction status certification-number referral-number]}]
  (cond-> {:member/id id}
    member-name                          (assoc :member/member-name member-name)
    ce-hours-completed                   (assoc :member/ce-hours-completed ce-hours-completed)
    ce-hours-required                    (assoc :member/ce-hours-required ce-hours-required)
    (some? complaint-unresolved?)        (assoc :member/complaint-unresolved? complaint-unresolved?)
    (some? certified?)                   (assoc :member/certified? certified?)
    (some? disciplined?)                 (assoc :member/disciplined? disciplined?)
    jurisdiction                        (assoc :member/jurisdiction jurisdiction)
    status                              (assoc :member/status status)
    certification-number                 (assoc :member/certification-number certification-number)
    referral-number                     (assoc :member/referral-number referral-number)))

(def ^:private member-pull
  [:member/id :member/member-name :member/ce-hours-completed :member/ce-hours-required
   :member/complaint-unresolved? :member/certified? :member/disciplined?
   :member/jurisdiction :member/status :member/certification-number :member/referral-number])

(defn- pull->member [m]
  (when (:member/id m)
    {:id (:member/id m) :member-name (:member/member-name m)
     :ce-hours-completed (:member/ce-hours-completed m)
     :ce-hours-required (:member/ce-hours-required m)
     :complaint-unresolved? (boolean (:member/complaint-unresolved? m))
     :certified? (boolean (:member/certified? m))
     :disciplined? (boolean (:member/disciplined? m))
     :jurisdiction (:member/jurisdiction m) :status (:member/status m)
     :certification-number (:member/certification-number m) :referral-number (:member/referral-number m)}))

(defrecord DatomicStore [conn]
  Store
  (member [_ id]
    (pull->member (d/pull (d/db conn) member-pull [:member/id id])))
  (all-members [_]
    (->> (d/q '[:find [?id ...] :where [?e :member/id ?id]] (d/db conn))
         (map #(pull->member (d/pull (d/db conn) member-pull [:member/id %])))
         (sort-by :id)))
  (complaint-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?mid
                :where [?k :complaint/member-id ?mid] [?k :complaint/payload ?p]]
              (d/db conn) id)))
  (assessment-of [_ member-id]
    (dec* (d/q '[:find ?p . :in $ ?mid
                :where [?a :assessment/member-id ?mid] [?a :assessment/payload ?p]]
              (d/db conn) member-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (certification-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :certification/seq ?s] [?e :certification/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (discipline-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :discipline/seq ?s] [?e :discipline/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-certification-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :certification-sequence/jurisdiction ?j] [?e :certification-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-discipline-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :discipline-sequence/jurisdiction ?j] [?e :discipline-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (member-already-certified? [s member-id]
    (boolean (:certified? (member s member-id))))
  (member-already-disciplined? [s member-id]
    (boolean (:disciplined? (member s member-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :member/upsert
      (d/transact! conn [(member->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/member-id (first path) :assessment/payload (enc payload)}])

      :complaint/set
      (d/transact! conn [{:complaint/member-id (first path) :complaint/payload (enc payload)}])

      :member/mark-certified
      (let [member-id (first path)
            {:keys [result member-patch]} (finalize-certification! s member-id)
            jurisdiction (:jurisdiction (member s member-id))
            next-n (inc (next-certification-sequence s jurisdiction))]
        (d/transact! conn
                     [(member->tx (assoc member-patch :id member-id))
                      {:certification-sequence/jurisdiction jurisdiction :certification-sequence/next next-n}
                      {:certification/seq (count (certification-history s)) :certification/record (enc (get result "record"))}])
        result)

      :member/mark-disciplined
      (let [member-id (first path)
            {:keys [result member-patch]} (finalize-discipline! s member-id)
            jurisdiction (:jurisdiction (member s member-id))
            next-n (inc (next-discipline-sequence s jurisdiction))]
        (d/transact! conn
                     [(member->tx (assoc member-patch :id member-id))
                      {:discipline-sequence/jurisdiction jurisdiction :discipline-sequence/next next-n}
                      {:discipline/seq (count (discipline-history s)) :discipline/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-members [s members]
    (when (seq members) (d/transact! conn (mapv member->tx (vals members)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:members ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [members]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-members s members))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo member set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))

(ns association.facts
  "Per-jurisdiction professional-membership-organization governance
  catalog -- the G2-style spec-basis table the Association Governance
  Governor checks every jurisdiction/assess proposal against ('did the
  advisor cite an OFFICIAL public source for this jurisdiction's
  professional-body governance/certification-accreditation
  requirements, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official governance
  body for professional/credentialing associations (see
  `:provenance`); they are a STARTING catalog, not a from-scratch
  survey of all ~194 jurisdictions. Extending coverage is additive: add
  one map to `catalog`, cite a real source, done -- never invent a
  jurisdiction's requirements to make coverage look bigger.

  Unlike a single-profession vertical (e.g. `clinic.facts`'s
  individual-practitioner-licensing bodies), a 'professional membership
  organization' spans many professions at once, so this catalog cites
  the GENERAL accreditation/governance framework applicable to
  professional bodies themselves in each jurisdiction, rather than any
  one profession's own regulator: Japan's Cabinet Office Public
  Interest Corporation Commission (which authorizes and supervises
  public-interest incorporated associations, the legal form most
  Japanese professional bodies register under), the US ANSI National
  Accreditation Board (which accredits certification bodies under
  ISO/IEC 17024), the UK Professional Standards Authority (which
  accredits registers of practitioners in non-statutorily-regulated
  professions), and Germany's federated Kammer (chamber) system.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  member-registration/standards-conformance/complaints-procedure/
  governance-disclosure evidence set submitted in some form;
  `:legal-basis` / `:owner-authority` / `:provenance` are the G2
  citation the governor requires before any :jurisdiction/assess
  proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "内閣府 公益認定等委員会 (Cabinet Office Public Interest Corporation Commission)"
          :legal-basis "公益社団法人及び公益財団法人の認定等に関する法律 (Act on Authorization of Public Interest Incorporated Associations and Foundations)"
          :national-spec "公益法人としての会員管理・懲戒手続きに関する運営基準"
          :provenance "https://www.koeki-info.go.jp/"
          :required-evidence ["会員登録記録 (member-registration record)"
                              "資格認定基準適合証明 (standards-conformance certificate)"
                              "苦情・懲戒処理手続き文書 (complaints-procedure document)"
                              "組織運営情報開示書 (governance-disclosure document)"]}
   "USA" {:name "United States"
          :owner-authority "ANSI National Accreditation Board (ANAB)"
          :legal-basis "ISO/IEC 17024 Personnel Certification Accreditation"
          :national-spec "Accredited personnel-certification-body operating requirements"
          :provenance "https://anab.ansi.org/"
          :required-evidence ["Member-registration record"
                              "Standards-conformance certificate"
                              "Complaints-procedure document"
                              "Governance-disclosure document"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Professional Standards Authority (PSA)"
          :legal-basis "Health and Social Care Act 2012 (Accredited Registers scheme)"
          :national-spec "PSA Accredited Registers standards for organisations holding voluntary registers"
          :provenance "https://www.professionalstandards.org.uk/"
          :required-evidence ["Member-registration record"
                              "Standards-conformance certificate"
                              "Complaints-procedure document"
                              "Governance-disclosure document"]}
   "DEU" {:name "Germany"
          :owner-authority "Kammern der Länder / Bundesverband der Freien Berufe (BFB)"
          :legal-basis "Berufsordnungen der Kammern unter den Kammergesetzen der Länder"
          :national-spec "Mitgliederverwaltung, Zertifizierung und Beschwerdeverfahren der Kammern"
          :provenance "https://www.freie-berufe.de/"
          :required-evidence ["Mitgliedsregistrierung (member-registration record)"
                              "Konformitätsnachweis (standards-conformance certificate)"
                              "Beschwerdeverfahrensdokument (complaints-procedure document)"
                              "Offenlegungsbericht (governance-disclosure document)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to issue a
  certification or finalize a disciplinary referral on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-9412 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `association.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))

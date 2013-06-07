;; The contents of this file are subject to the LGPL License, Version 3.0.
;;
;; Copyright (C) 2013, Phillip Lord, Newcastle University
;;
;; This program is free software: you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as published by
;; the Free Software Foundation, either version 3 of the License, or (at your
;; option) any later version.
;;
;; This program is distributed in the hope that it will be useful, but WITHOUT
;; ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
;; FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
;; for more details.
;;
;; You should have received a copy of the GNU Lesser General Public License
;; along with this program. If not, see http://www.gnu.org/licenses/.
(in-ns 'tawny.owl)


;; data type properties
(defn- ensure-data-property [property]
  "Ensures that 'property' is an data property,
converting it from a string or IRI if necessary."
  (cond
   (instance? OWLDataProperty property)
   property
   (instance? IRI property)
   (.getOWLDataProperty
    ontology-data-factory property)
   (instance? String property)
   (ensure-data-property
    (iriforname property))
   :default
   (throw (IllegalArgumentException.
           (format "Expecting an OWL data property: %s" property)))))

(def
  ^{:doc "Adds a domain to a data property."
    :arglists '([property & domains] [ontology property & domains])}
  add-data-domain
  (ontology-vectorize
   (fn add-data-domain
     [property domain]
     (add-axiom
      (.getOWLDataPropertyDomainAxiom
       ontology-data-factory
       (ensure-data-property property)
       (ensure-class domain))))))

(def
  ^{:doc "Adds a range to a data property."
    :arglists '([property & ranges] [ontology property & ranges])}
  add-data-range
  (ontology-vectorize
   (fn add-data-range
     [property range]
     (add-axiom
      (.getOWLDataPropertyRangeAxiom
       ontology-data-factory
       (ensure-data-property property)
       range)))))


(def xsd:float
  (.getFloatOWLDatatype ontology-data-factory))

(def xsd:double
  (.getDoubleOWLDatatype ontology-data-factory))

(def xsd:integer
  (.getIntegerOWLDatatype ontology-data-factory))

(def rdf:plainliteral
  (.getRDFPlainLiteral ontology-data-factory))

;; TODO
;; need to create accessor methods for all the data ranges. Sadly, also need
;; to think what to do about or and and, which they also support. In the ideal
;; world I don't want to introduce new functions, although I have already had
;; to with add-data-range and so on. What a pain in the ass!
;; the ensure-class/property things can be extended to for an IRI to check if
;; it is already in the signature of any known ontology

(defn datatypeproperty-explicit
  "Define a new datatype property with an explicit map"
  [property map]
  (let [ontology (or (first (get map :ontology))
                            (get-current-ontology))
        dataproperty (ensure-data-property property)]
    (.addAxiom owl-ontology-manager
               ontology
               (.getOWLDeclarationAxiom
                ontology-data-factory
                dataproperty))
    (add-annotation ontology dataproperty (:annotation map))
    (add-data-domain ontology dataproperty (:domain map))
    (add-data-range ontology dataproperty (:range map))
    (when-let [comment (:comment map)]
      (add-annotation ontology
                      dataproperty
                      (list (owlcomment (first comment)))))

    (when-let [labl (:label map)]
      (println labl)
      (add-annotation ontology dataproperty
                      (list (label (first labl)))))

    dataproperty
    ))

(defn datatypeproperty
  "Define a new datatype property"
  [name & frames]
  (datatypeproperty-explicit
   name
   (util/check-keys
    (merge-with concat
                (util/hashify
                 frames)
                *default-frames*)
    [:domain :range :annotation :characteristics
     :subproperty :equivalent :disjoint :ontology
     :label :comment])))

(defmacro defdproperty
  [dataname & frames]
  `(let [namestring# (name '~dataname)
         datatype# (tawny.owl/datatypeproperty namestring#
                                               ~@frames)]
     (def
       ~(vary-meta dataname
                   merge
                   {:owl true})
       datatype#)))


(defn get-literal
  "Returns a OWL2 literal.

`literal' is the value of the literal and must be a string or a number. Anything
else must by coerced into a string manually. Options can also be specified,
with :lang definining the language where `literal' is a string, and :type
which is an OWLDatatype object.
"
  [literal & {:keys [lang type]}]
  (cond
   lang
   (.getOWLLiteral ontology-data-factory literal lang)
   type
   (.getOWLLiteral ontology-data-factory literal type)
   :default
   (.getOWLLiteral ontology-data-factory literal)))


(defn datatype-explicit [name frame]
  (let [ontology
        (or (first (get frame :ontology))
            (get-current-ontology))
        datatype
        (.getOWLDatatype
         ontology-data-factory
         (iriforname name))]
    (add-axiom ontology
     (.getOWLDeclarationAxiom ontology-data-factory datatype))

    (add-annotation
     ontology datatype
     (concat
      (:annotation frame)
      (map label (:label frame))
      (map owlcomment (:comment frame))))

    (doseq
        [n (:equivalent frame)]
      (add-axiom ontology
       (.getOWLDatatypeDefinitionAxiom
        ontology-data-factory datatype n)))
    datatype))


(defn datatype [name & frames]
  (datatype-explicit
   name
   (util/check-keys
    (merge-with
     concat
     (util/hashify frames)
     *default-frames*)
    [:equivalent :annotation :label :comment])))


(defmacro defdatatype
  [dataname & frames]
  `(let [namestring# (name '~dataname)
         datatype# (tawny.owl/datatype namestring#
                                       ~@frames)]
     (def
       ~(vary-meta dataname
                   merge
                   {:owl true})
       datatype#)))



(defn dataand
  [& types]
  (.getOWLDataIntersectionOf
   ontology-data-factory
   (into #{} types)))

(defn dataor
  [& types]
  (.getOWLDataUnionOf
   ontology-data-factory
   (into #{} types)))

(defn datanot
  [type]
  (.getOWLDataComplementOf
   ontology-data-factory type))

(defn ><
  [from to]
  (.getOWLDatatypeMinMaxExclusiveRestriction
   ontology-data-factory from to))

(defn >=<
  [from to]
  (.getOWLDatatypeMinMaxInclusiveRestriction
   ontology-data-factory from to))

(defn dataoneof [& data]
  (.getOWLDataOneOf
   ontology-data-factory
   (into #{} data)))
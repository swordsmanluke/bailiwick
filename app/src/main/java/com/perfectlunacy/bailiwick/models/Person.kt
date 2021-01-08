package com.perfectlunacy.bailiwick.models

import org.semanticweb.yars.nx.Node as RdfNode
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class Person (
    val id: String,
    val name: String,
    val photo_loc: String?,
    val birthday: LocalDate?,
    // TODO: The below can/should probably be owned by classes that can do smart things with them
    val mailbox: List<String>,
    val homepage: List<String>
    ) {

    companion object {
        val SUBJECT = 0
        val PREDICATE = 1
        val OBJECT = 2

        fun fromNodes(nodes: List<Array<RdfNode>>): Person? {
            // TODO: Clean this up to use constants for the various standards, etc.
            val rdf_type = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
            val foaf_person = "http://xmlns.com/foaf/0.1/Person"
            val foaf_name = "http://xmlns.com/foaf/0.1/name"
            val foaf_mbox = "http://xmlns.com/foaf/0.1/mbox"
            val foaf_homepage = "http://xmlns.com/foaf/0.1/homepage"
            val foaf_birthday = "http://xmlns.com/foaf/0.1/birthday"
            val foaf_depiction = "http://xmlns.com/foaf/0.1/depiction"

            // validate this is a Person type
            val type = nodeFor(nodes, rdf_type) ?: return null
            if (type[OBJECT].label != foaf_person) return null

            // Extract the name or fail
            val name = nodeFor(nodes, foaf_name) ?: return null

            // Ok, now we've got the name, everything else is either optional, or just easy
            val firstNode = nodes.first()

            return Person(
                firstNode[SUBJECT].label, // TODO: Group triples by subject before passing around
                name[OBJECT].label,
                nodeFor(nodes, foaf_depiction)?.get(OBJECT)?.label,
                birthdate(nodeFor(nodes, foaf_birthday)?.get(OBJECT)?.label),
                nodesFor(nodes, foaf_mbox).map{ n -> n[OBJECT].label },
                nodesFor(nodes, foaf_homepage).map{ n -> n[OBJECT].label })
        }

        private fun birthdate(dateStr: String?): LocalDate? {
            if(dateStr==null) return null
            try {
                return LocalDate.parse("${LocalDate.now().year}-$dateStr")
            } catch(_: DateTimeParseException) { return null }

        }

        private fun nodeFor(
            nodes: List<Array<RdfNode>>,
            predicate: String
        ) = nodes.firstOrNull { node -> node[PREDICATE].label == predicate }

        private fun nodesFor(
            nodes: List<Array<RdfNode>>,
            predicate: String
        ) = nodes.filter { node -> node[PREDICATE].label == predicate }
    }
}
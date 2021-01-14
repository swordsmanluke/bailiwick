package com.perfectlunacy.bailiwick.models

import org.junit.Assert
import org.junit.Test
import org.semanticweb.yars.rdfxml.RdfXmlParser
import java.nio.charset.StandardCharsets

class PersonDeserializationTest {
    val xml_person_def = """
        <rdf:RDF
              xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
              xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
              xmlns:foaf="http://xmlns.com/foaf/0.1/"
              xmlns:admin="http://webns.net/mvcb/">
            <foaf:Person rdf:ID="lucas_taylor">
                <foaf:name>Lucas Taylor</foaf:name>
                <foaf:givenname>Lucas</foaf:givenname>
                <foaf:family_name>Taylor</foaf:family_name>
                <foaf:mbox rdf:resource="mailto:lukeis@example.com"/>
                <foaf:homepage rdf:resource="http://lukeis.example.com"/>
                <foaf:homepage rdf:resource="ipns/k51qzi5uqu5dhmerosojhfx89g7n6p7ces5t85y1lm4difs457wbzmdkpgg0b3"/>
                <foaf:depiction rdf:resource="http://example.com/me.png"/>
                <foaf:workplaceHomepage rdf:resource="flexe.com"/>
                <foaf:workInfoHomepage rdf:resource="https://en.wikipedia.org/wiki/Computer_programming"/>
            </foaf:Person>
        </rdf:RDF>
    """.trimIndent()


    @Test
    fun can_convert_xml_rdf_into_person() {
        val parser = RdfXmlParser()
        val nodes = parser.parse(xml_person_def.byteInputStream(StandardCharsets.UTF_8), "ipns://bailiwick/")
        val person = Person.fromNodes(nodes.asSequence().toList()) // TODO: Yeah, this is fugly
        person!!

        Assert.assertEquals("Lucas Taylor", person.name)
        Assert.assertEquals("mailto:lukeis@example.com", person.mailbox.firstOrNull())
    }

}
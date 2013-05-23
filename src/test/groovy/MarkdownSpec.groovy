package test.groovy

import org.plavelo.markdown.Markdown
import spock.lang.*

class MarkdownSpec extends spock.lang.Specification {
	
	def setup() {
	}
	
	def cleanup() {
	}
	
	def setupSpec() {
	}
	
	def cleanupSpec() {
	}
	
	def "Convert tabs to spaces"() {
		expect:
		new Markdown().detabify(tabbed) == converted

		where:
		tabbed               | converted
		"\tsome text"        | "    some text"
		"\t\tsome\ttext\t"   | "        some    text    "
		"\t\tsome\n\ttext\t" | "        some\n    text    "
		"some text"          | "some text"
	}
}

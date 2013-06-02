package test.groovy

import org.plavelo.markdown.*

public class MarkdownSpec extends spock.lang.Specification {
	
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

    def "Convert headers"() {
        expect:
        new Markdown().convertHeaders(before) == converted

        where:
        before                     | converted
        "setext style\n====="      | "<h1>setext style</h1>\n"
        "setext style\n-------"    | "<h2>setext style</h2>\n"
        "# atx style"              | "<h1>atx style</h1>\n"
        "## atx style"             | "<h2>atx style</h2>\n"
        "### atx style"            | "<h3>atx style</h3>\n"
        "#### atx style"           | "<h4>atx style</h4>\n"
        "##### atx style"          | "<h5>atx style</h5>\n"
        "###### atx style"         | "<h6>atx style</h6>\n"
        "####### atx style"        | "<h6># atx style</h6>\n"
        "# atx style2 #"           | "<h1>atx style2</h1>\n"
        "## atx style2 ##"         | "<h2>atx style2</h2>\n"
        "### atx style2 ####"      | "<h3>atx style2</h3>\n"
        "#### atx style2 ##"       | "<h4>atx style2</h4>\n"
        "##### atx style2 #"       | "<h5>atx style2</h5>\n"
        "###### atx style2 #####"  | "<h6>atx style2</h6>\n"
        "####### atx style2 #####" | "<h6># atx style2</h6>\n"
    }


    def "Convert block quotes"() {
        expect:
        new Markdown().convertBlockQuotes(before) == converted

        where:
        before             | converted
        "> some text"      | "<blockquote>some text</blockquote>\n"
    }
}

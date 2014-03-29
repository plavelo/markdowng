package test.groovy

import spock.lang.*

import org.plavelo.markdown.Markdown

public class MarkdownSpec extends Specification {

    def setup() {
    }

    def cleanup() {
    }

    def setupSpec() {
    }

    def cleanupSpec() {
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
        before                          | converted
        "> some text"                   | "<blockquote>\n  <p>some text</p>\n</blockquote>\n"
        "> some text\nsome text\n"      | "<blockquote>\n  <p>some text\n  some text</p>\n</blockquote>\n"
        "> some text\n# atx style\n"    | "<blockquote>\n  <p>some text</p>\n  <h1>atx style</h1>\n</blockquote>\n"
        "> some text\n> > nested\n"     | "<blockquote>\n  <p>some text</p>\n  <blockquote>\n    <p>nested</p>\n  </blockquote>\n</blockquote>\n"
    }

    def "Convert phrase emphasis"() {
        expect:
        new Markdown().convertPhraseEmphasis(before) == converted

        where:
        before                                    | converted
        "*asterisk*"                              | "<em>asterisk</em>"
        "_underscore_"                            | "<em>underscore</em>"
        "**asterisk**"                            | "<strong>asterisk</strong>"
        "__underscore__"                          | "<strong>underscore</strong>"
    }

    def "Convert lists"() {
        expect:
        new Markdown().convertLists(before) == converted

        where:
        before                                                          | converted
        "* item 1\n* item 2\n"                                          | "<ul>\n  <li>item 1</li>\n  <li>item 2</li>\n</ul>\n"
        "+ item 1\n+ item 2\n"                                          | "<ul>\n  <li>item 1</li>\n  <li>item 2</li>\n</ul>\n"
        "- item 1\n- item 2\n"                                          | "<ul>\n  <li>item 1</li>\n  <li>item 2</li>\n</ul>\n"
        "1. item 1\n1. item 2\n"                                        | "<ol>\n  <li>item 1</li>\n  <li>item 2</li>\n</ol>\n"
        "1. item 1\n2. item 2\n"                                        | "<ol>\n  <li>item 1</li>\n  <li>item 2</li>\n</ol>\n"
        "1. item 1\nnext row\n2. item 2\n"                              | "<ol>\n  <li>item 1\n  next row</li>\n  <li>item 2</li>\n</ol>\n"
        "1. item 1\n\n2. item 2\n"                                      | "<ol>\n  <li><p>item 1</p></li>\n  <li><p>item 2</p></li>\n</ol>\n"
        "1. level 1\n    2. level 2\n        3. level 3\n1. level 1\n"  | "<ol>\n  <li>level 1\n    <ol>\n      <li>level 2\n        <ol>\n          <li>level 3</li>\n        </ol>\n      </li>\n    </ol>\n  </li>\n  <li>level 1</li>\n</ol>\n"
    }

    def "Convert link"() {
        expect:
        def markdown = new Markdown()
        def processed = markdown.convertLinkDefinitions(before)
        markdown.convertLink(processed) == converted

        where:
        before                                                          | converted
        "[an example](http://example.com/)"                             | "<a href=\"http://example.com/\">an example</a>"
        "[an example](http://example.com/ \"Title\")"                   | "<a href=\"http://example.com/\" title=\"Title\">an example</a>"
        "[an example](/example/)"                                       | "<a href=\"/example/\">an example</a>"
        "[an example](/example/ \"Title\")"                             | "<a href=\"/example/\" title=\"Title\">an example</a>"
        "[an example][1]\n\n[1]: http://example.com/"                   | "<a href=\"http://example.com/\">an example</a>\n\n"
        "[an example][1]\n\n[1]: http://example.com/ \"Title\""         | "<a href=\"http://example.com/\" title=\"Title\">an example</a>\n\n"
        "[an example][1]\n\n[1]: http://example.com/ 'Title'"           | "<a href=\"http://example.com/\" title=\"Title\">an example</a>\n\n"
        "[an example][1]\n\n[1]: http://example.com/ (Title)"           | "<a href=\"http://example.com/\" title=\"Title\">an example</a>\n\n"
        "[an example][1]\n\n[1]: <http://example.com/> \"Title\""       | "<a href=\"http://example.com/\" title=\"Title\">an example</a>\n\n"
        "[an example][1]\n\n[1]: \n    http://example.com/ \"Title\""   | "<a href=\"http://example.com/\" title=\"Title\">an example</a>\n\n"
        "[an example][]\n\n[an example]: http://example.com/ \"Title\"" | "<a href=\"http://example.com/\" title=\"Title\">an example</a>\n\n"
    }

    def "Convert auto links"() {
        expect:
        new Markdown().convertAutoLinks(before) == converted

        where:
        before                    | converted
        "<http://example.com/>\n" | "<a href=\"http://example.com/\">http://example.com/</a>\n"
        //"<address@example.com>\n" | "<a href=\"random strings\">random strings</a>\n"
    }

    def "Convert image"() {
        expect:
        def markdown = new Markdown()
        def processed = markdown.convertLinkDefinitions(before)
        markdown.convertImage(processed) == converted

        where:
        before                                                     | converted
        "![alt text](/path/to/img.jpg \"title\")"                  | "<img src=\"/path/to/img.jpg\" alt=\"alt text\" title=\"title\" />"
        //"![alt text][id]\n[id]: /path/to/img.jpg \"title\""        | "<img src=\"/path/to/img.jpg\" alt=\"alt text\" title=\"title\" />"
    }

    def "Convert code block"() {
        expect:
        new Markdown().convertCodeBlocks(before) == converted

        where:
        before                                                                        | converted
        "This is a normal paragraph:\n\n    lang:text\n    This is a code block.\n" | "This is a normal paragraph:\n\n<pre class=\"text\">\n  This is a code block.\n</pre>\n\n"
        "This is a normal paragraph:\n\n    This is a code block.\n"                  | "This is a normal paragraph:\n\n<pre>\n  <code>\n    This is a code block.\n  </code>\n</pre>\n\n"
    }

    def "Convert code span"() {
        expect:
        new Markdown().convertCodeSpans(before) == converted

        where:
        before                                        | converted
        "some `code` text."                           | "some <code>code</code> text."
        "``There is a literal backtick (`) here. ``"  | "<code>There is a literal backtick (`) here.</code>"
        "`` ` ``"                                     | "<code>`</code>"
        "`` `foo` ``"                                 | "<code>`foo`</code>"
    }
    
    def "Convert horizontal rules"() {
        expect:
        new Markdown().convertHorizontalRules(before) == converted

        where:
        before                                    | converted
        "* * *"                                   | "<hr />\n"
        "***"                                     | "<hr />\n"
        "*****"                                   | "<hr />\n"
        "- - -"                                   | "<hr />\n"
        "---------------------------------------" | "<hr />\n"
        "  _ _ _"                                 | "<hr />\n"
    }

    def "Convert Line breaks"() {
        expect:
        new Markdown().convertBreak(before) == converted

        where:
        before       | converted
        "a  \nb  \n" | "a <br />\nb <br />\n"
    }

    //def "Auto escape"() {
    //    expect:
    //    def markdown = new Markdown()
    //    def processed = markdown.escapeCharacters(before)
    //    markdown.unEscapeCharacters(processed) == converted

    //    where:
    //    before                 | converted
    //    /</                    | "&lt;"
    //    /&/                    | "&amp;"
    //    /\\ backslash/         | "\\ backslash"
    //    /\` backtick/          | "` backtick"
    //    /\* asterisk/          | "* asterisk"
    //    /\_ underscore/        | "_ underscore"
    //    /\{} curly braces/     | "{} curly braces"
    //    /\[] squire brackets/  | "[] squire brackets"
    //    /\() parentheses/      | "() parentheses"
    //    /\# hash mark/         | "# hash mark"
    //    /\+ plus sign/         | "+ plus sign"
    //    /\- hyphen/            | "- hyphen"
    //    /\. dot/               | ". dot"
    //    /\! exclamation mark/  | "! exclamation mark"
    //}
}

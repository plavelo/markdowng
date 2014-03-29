package org.plavelo.markdown

import java.util.regex.Matcher

public class Markdown {
    private static final CharacterProtector HTML_PROTECTOR = new CharacterProtector()
    private static final CharacterProtector CHAR_PROTECTOR = new CharacterProtector()

    def linkDefinitions = [:]

    def tabWidth = 4
    def htmlIndentWidth = 2

    def markdown(text) {
        if (text == null) {
            return ""
        }

        text = text.replaceAll(/(?m)\r\n/, /\n/)
        text = text.replaceAll(/(?m)\r/, /\n/)
        text = text.replaceAll(/\t/, " " * tabWidth)
        text = text.replaceAll(/(?m)^[ ]+$/, "")

        text = hashHTMLBlocks(text)
        text = convertLinkDefinitions(text)
        text = convertBlock(text)
        text = unEscapeCaracters(text)
        text = text.replaceAll(/(?m)^\s*\n/, "")
        text
    }

    def convertBlock(text) {
        text = convertHeaders(text)
        text = convertHorizontalRules(text)
        text = convertLists(text)
        text = convertCodeBlocks(text)
        text = convertBlockQuotes(text)
        text = hashHTMLBlocks(text)
        text = convertParagraphs(text)
        text
    }

    def convertSpan(text) {
        text = escapeSpecialCharsWithinTagAttributes(text)
        text = convertCodeSpans(text)
        text = encodeBackslashEscapes(text)
        text = convertImage(text)
        text = convertLink(text)
        text = convertAutoLinks(text)
        text = escapeSpecialCharsWithinTagAttributes(text)
        text = encodeAmpsAndAngles(text)
        text = convertPhraseEmphasis(text)
        text = convertBreak(text)
        text
    }

    def convertHeaders(text) {
        text = text.replaceAll(/(?m)^(.*)\n====+$/, '<h1>$1</h1>\n')
        text = text.replaceAll(/(?m)^(.*)\n----+$/, '<h2>$1</h2>\n')
        text = text.replaceAll(/(?m)^(#{1,6})\s*(.*?)\s*#*$/) {
            def marker = it[1]
            def heading = it[2]
            def tag = "h${marker.length()}"
            "<${tag}>${heading}</${tag}>\n"
        }
    }

    def convertBlockQuotes(text) {
        text = text.replaceAll(/(?m)((^[ \t]*>[ \t]?.+(\n.+)*\n*)+)/) {
            def blockQuote = it[1]
            blockQuote = blockQuote.replaceAll(/(?m)^[ \t]*>[ \t]?/, '')
            blockQuote = blockQuote.replaceAll(/(?m)^[ \t]+$/, '')
            blockQuote = convertBlock(blockQuote)
            blockQuote = blockQuote.replaceAll(/(?m)^/, "  ")
            blockQuote = blockQuote.replaceAll(/(?s)(\s*<pre>.*?<\/pre>)/) { w, pre ->
                pre.replaceAll(/^  /, '')
            }
            "<blockquote>\n${blockQuote}\n</blockquote>\n";
        }
    }
    
    def convertPhraseEmphasis(text) {
        text = text.replaceAll(/(\*\*|__)(?=\S)(.+?[*_]*)(?<=\S)\1/, '<strong>$2</strong>')
        text = text.replaceAll(/(\*|_)(?=\S)(.+?)(?<=\S)\1/, '<em>$2</em>')
    }

    def convertBreak(text) {
        text = text.replaceAll(/ {2,}\n/, " <br />\n")
    }

    def convertLists(text, recursion=false) {
        def listSignPattern = /([-+*]|\d+[.])[ ]+/
        def listBlockPattern = /(?m)(?s)(^$listSignPattern(.+?)(\n{2,}|\z))+/
        text = text.replaceAll(listBlockPattern) {
            def listBlock = it[0].replaceAll(/\n{2,}/, "\n\n\n")
            def listType = it[2] ==~ /[*+-]/ ? "ul" : "ol"
            def listPattern = /(?m)(?s)(^\n)?^$listSignPattern((.+?\n?)(\n|\z))(?=(\n*^$listSignPattern)|\z)/
            listBlock = listBlock.replaceAll(listPattern) {
                def list = it[3]
                def isBlock = list.contains("\n\n") || (it[1] != null)
                list = outdent(list)
                if (!list.find(listPattern)) {
                    list = list.replaceAll(/\n+$/, "")
                }
                if (isBlock) {
                    list = convertBlock(list)
                } else {
                    list = convertLists(list, true)
                    list = convertSpan(list)
                }
                list = "<li>${list}</li>\n"
                list = indent(list)
                list
            }
            listBlock = "<${listType}>\n${listBlock}</${listType}>\n"
            if (recursion) {
                listBlock = indent(listBlock)
            }
            listBlock
        }
    }

    def convertLink(text) {
        def converter = {whole, url, title, content ->
            url = url.replaceAll(/\*/, CHAR_PROTECTOR.encode("*"))
            url = url.replaceAll(/_/, CHAR_PROTECTOR.encode("_"))
            def titleTag = ""
            if (title != null && !title.equals("")) {
                title = title.replaceAll(/\*/, CHAR_PROTECTOR.encode("*"))
                title = title.replaceAll(/_/, CHAR_PROTECTOR.encode("_"))
                titleTag = " title=\"$title\""
            }
            "<a href=\"$url\"$titleTag>$content</a>"
        }
        def defConverter = {whole, id, content ->
            def replacement = ""
            def definition = linkDefinitions.get(id)
            if (definition != null) {
                def url = definition.get("url")
                def title = definition.get("title")
                replacement = converter(whole, url, title, content)
            } else {
                replacement = whole
            }
            replacement
        }
        // convert internal references: [link text] [id]
        def internalLink = /(\[(.*?)\][ ]?(?:\n[ ]*)?\[(.*?)\])/
        text = text.replaceAll(internalLink) {
            def whole = it[1]
            def linkText = it[2]
            // for shortcut links like [this][]
            def id = (it[3] ? it[3] : linkText).toLowerCase()
            defConverter(whole, id, linkText)
        }
        // convert inline-style links: [link text](url "optional title")
        def inlineLink = /(?m)(\[(.*?)\]\([ \t]*<?(.*?)>?[ \t]*((['\"])(.*?)\5)?\))/
        text = text.replaceAll(inlineLink) {
            def url = it[3]
            def title = it[6]
            def linkText = it[2]
            converter("", url, title, linkText)
        }
        // handle reference-style shortcuts: [link text]
        def referenceShortcut = /(?m)(\[([^\[\]]+)\])/
        text = text.replaceAll(referenceShortcut) {
            def whole = it[1]
            def linkText = it[2]
            def id = linkText.toLowerCase().replaceAll(/[ ]?\n/, " ") // change embedded newlines into spaces
            defConverter(whole, id, linkText)
        }
        text
    }

    def convertLinkDefinitions(text) {
        def p = /(?m)^[ ]{0,3}\[(.+)\]:[ \t]*\n?[ \t]*<?(\S+?)>?[ \t]*\n?[ \t]*(?:["'(](.+?)["')][ \t]*)?(?:\n+|\Z)/
        text = text.replaceAll(p) {
            def id = it[1].toLowerCase()
            def url = encodeAmpsAndAngles(it[2])
            def title = it[3] != null ? it[3] : ""
            title = title.replaceAll(/"/, "&quot;")
            linkDefinitions.put(id, ["url":url, "title":title])
            ""
        }
    }

    def convertAutoLinks(text) {
        text = text.replaceAll(/<((https?|ftp):[^'">\s]+)>/, "<a href=\"\$1\">\$1</a>")
        def emailPattern = /<([-.\w]+\@[-a-z0-9]+(?:\.[-a-z0-9]+)*\.[a-z]+)>/
        text = text.replaceAll(emailPattern) {
            def address = it[1]
            address = unEscapeCaracters(address)
            def url = encodeEmail("mailto:" + address)
            address = encodeEmail(address)
            "<a href=\"$url\">$address</a>"
        }
        text
    }

    def convertImage(text) {
        text = text.replaceAll(/!\[(.*)\]\((.*) "(.*)"\)/, "<img src=\"\$2\" alt=\"\$1\" title=\"\$3\" />")
        text = text.replaceAll(/!\[(.*)\]\((.*)\)/, "<img src=\"\$2\" alt=\"\$1\" />")
        text
    }
    
    def convertCodeBlocks(text) {
        def p = /(?m)(?:\n\n|\A)((?:(?:[ ]{4}).*\n+)+)((?=^[ ]{0,4}\S)|\Z)/
        text = text.replaceAll(p) {
            def LANG_IDENTIFIER = "lang:"
            def codeBlock = it[1]
            codeBlock = outdent(codeBlock)
            codeBlock = escapeCharacters(codeBlock)
            codeBlock = codeBlock.replaceAll(/\A\n+/, "").replaceAll(/\s+\z/,"")
            def firstLine = codeBlock ? codeBlock.split("\\n")[0] : ""

            def replacement = ""
            if (codeBlock && codeBlock.startsWith(LANG_IDENTIFIER)) {
                def lang = firstLine.replaceFirst(LANG_IDENTIFIER, "").trim()
                codeBlock = codeBlock.replaceFirst("$firstLine\n", "")
                replacement = "\n\n<pre class=\"$lang\">\n${indent(codeBlock)}\n</pre>\n\n"
            } else {
                replacement = "\n\n<pre>\n${indent("<code>\n${indent(codeBlock)}\n</code>")}\n</pre>\n\n"
            }
            replacement
        }
        text
    }

    def convertCodeSpans(text) {
        text = text.replaceAll(/(?<!\\)(`+)(.+?)(?<!`)\1(?!`)/) {
            def code = it[2]
            code = code.replaceAll(/^[ \t]+/, "").replaceAll(/[ \t]+$/, "")
            code = escapeCharacters(code)
            "<code>$code</code>"
        }
    }
    
    def convertHorizontalRules(text) {
        for (def delimiter : [/\*/, /-/, /_/]) {
            text = text.replaceAll(/^[ ]{0,2}([ ]?$delimiter[ ]?){3,}[ ]*$/, "<hr />\n")
        }
        text
    }

    def convertParagraphs(text) {
        text = text.replaceAll(/\A\n+/, "")
        text = text.replaceAll(/\n+\z/, "")

        def paragraphs = []
        def isEmpty =  text.length() == 0
        if (isEmpty) {
            paragraphs = []
        } else {
            paragraphs = text.split(/\n{2,}/)
        }
        for (int i = 0; i < paragraphs.length; i++) {
            def paragraph = paragraphs[i]
            def decoded = HTML_PROTECTOR.decode(paragraph)
            if (decoded != null) {
                paragraphs[i] = decoded
            } else {
                paragraph = convertSpan(paragraph)
                paragraphs[i] = "<p>" + paragraph + "</p>"
            }
        }
        paragraphs.join("\n")
    }

    def escapeSpecialCharsWithinTagAttributes(text) {
        def replacement = ""
        Collection<HTMLToken> tokens = tokenizeHTML(text)
        for (HTMLToken token : tokens) {
            def value = token.getText()
            if (token.isTag()) {
                value = value.replaceAll("\\\\", CHAR_PROTECTOR.encode("\\"))
                value = value.replaceAll("`", CHAR_PROTECTOR.encode("`"))
                value = value.replaceAll("\\*", CHAR_PROTECTOR.encode("*"))
                value = value.replaceAll("_", CHAR_PROTECTOR.encode("_"))
            }
            replacement += value
        }
        replacement
    }

    def escapeCharacters(text) {
        text = text.replaceAll("&", "&amp;")
        text = text.replaceAll("<", "&lt;")
        text = text.replaceAll(">", "&gt;")
        text = text.replaceAll("\\*", CHAR_PROTECTOR.encode("*"))
        text = text.replaceAll("\\_", CHAR_PROTECTOR.encode("_"))
        text = text.replaceAll("\\{", CHAR_PROTECTOR.encode("{"))
        text = text.replaceAll("\\}", CHAR_PROTECTOR.encode("}"))
        text = text.replaceAll("\\[", CHAR_PROTECTOR.encode("["))
        text = text.replaceAll("\\]", CHAR_PROTECTOR.encode("]"))
        text = text.replaceAll("\\\\", CHAR_PROTECTOR.encode("\\"))
        text
    }

    def unEscapeCaracters(text) {
        for (def hash : CHAR_PROTECTOR.getAllEncodedTokens()) {
            def plaintext = CHAR_PROTECTOR.decode(hash)
            text = text.replaceAll(hash, plaintext)
        }
        text
    }

    def encodeEmail(text) {
        def encoded = ""
        char[] email = text.toCharArray()
        for (char ch : email) {
            double dice = Math.random()
            if (dice < 0.45) {
                // Decimal
                encoded += "&#${(int) ch};"
            } else if (dice < 0.9) {
                // Hex
                encoded += "&#x${Integer.toString((int) ch, 16)};"
            } else {
                encoded += "$ch"
            }
        }
        encoded
    }

    def encodeAmpsAndAngles(text) {
        text.replaceAll(/&(?!#?[xX]?(?:[0-9a-fA-F]+|\w+);)/, "&amp;")
        text.replaceAll(/<(?![a-z\/?\\$!])/, "&lt;")
        text
    }

    def encodeBackslashEscapes(text) {
        def normalChars = "`_>!".toCharArray()
        def escapedChars = "*{}[]()#+-.".toCharArray()
        def encodeEscapes = {content, chars, slashes ->
            for (char ch : chars) {
                def regex = slashes + ch
                content = content.replaceAll(regex, CHAR_PROTECTOR.encode(String.valueOf(ch)))
            }
            content
        }

        // Two backslashes in a row
        text.replaceAll("\\\\\\\\", CHAR_PROTECTOR.encode("\\"))

        // Normal characters don't require a backslash in the regular expression
        text = encodeEscapes(text, normalChars, "\\\\")
        text = encodeEscapes(text, escapedChars, "\\\\\\")

        text
    }

    def indent(text) {
        text.replaceAll(/(?m)^/, " " * htmlIndentWidth)
    }

    def outdent(text) {
        text.replaceAll(/(?m)^(\t|[ ]{1,${tabWidth}})/, "")
    }

    def hashHTMLBlocks(text) {
        def tagsA = [
                "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "table",
                "dl", "ol", "ul", "script", "noscript", "form", "fieldset", "iframe", "math"
        ]
        def tagsB = ["ins", "del"]
        def alternationA = tagsA.join("|")
        def alternationB = alternationA + "|" + tagsB.join("|")

        // First, look for nested blocks, e.g.:
        //   <div>
        //       <div>
        //       tags for inner block must be indented.
        //       </div>
        //   </div>
        //
        // The outermost tags must start at the left margin for this to match, and
        // the inner nested divs must be indented.
        // We need to do this before the next, more liberal match, because the next
        // match will start at the first `<div>` and stop at the first `</div>`.
        def p1 = /(?m)(^<($alternationA)\b(.*\n)*?<\/\2>[ ]*(?=\n+|\Z))/

        // Now match more liberally, simply from `\n<tag>` to `</tag>\n`
        def p2 = /(?m)(^<($alternationB)\b(.*\n)*?.*<\/\2>[ ]*(?=\n+|\Z))/

        // Special case for <hr>
        def p3 = /(?m)(?:(?<=\n\n)|\A\n?)([ ]{0,${tabWidth-1}}<(hr)\b([^<>])*?\/?>[ ]*(?=\n{2,}|\Z))/

        // Special case for standalone HTML comments:
        def p4 = /(?m)(?:(?<=\n\n)|\A\n?)([ ]{0,${tabWidth-1}}(?s:<!(--.*?--\s*)+>)[ ]*(?=\n{2,}|\Z))/

        [p1, p2, p3, p4].each { p ->
            text = text.replaceAll(p) {
                def literal = it[0]
                "\n\n${HTML_PROTECTOR.encode(literal)}\n\n"
            }
        } 
        text
    }

    private Collection<HTMLToken> tokenizeHTML(String text) {
        List<HTMLToken> tokens = new ArrayList<HTMLToken>()
        def nestedTags = nestedTagsRegex(6)

        def p = /(?i)(?s:<!(--.*?--\s*)+>)|(?i)(?s:<\?.*?\?>)|(?i)$nestedTags/

        int lastPos = 0
        Matcher m = (text =~ p)
        while (m.find()) {
            if (lastPos < m.start()) {
                tokens.add(HTMLToken.text(text.substring(lastPos, m.start())))
            }
            tokens.add(HTMLToken.tag(text.substring(m.start(), m.end())))
            lastPos = m.end()
        }
        if (lastPos < text.length()) {
            tokens.add(HTMLToken.text(text.substring(lastPos, text.length())))
        }
        tokens
    }

    def nestedTagsRegex(depth) {
        if (depth == 0) {
            return ""
        }
        "(?:<[a-z/!\$](?:[^<>]|${nestedTagsRegex(depth - 1)})*>)"
    }
}

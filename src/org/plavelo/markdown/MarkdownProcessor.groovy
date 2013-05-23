package org.plavelo.markdown
import CharacterProtector;
import LinkDefinition;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

File file = new File( "/Users/plavelo/Desktop/markdown.txt" );
BufferedReader br = null;
try {
	br = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));
	// 読み込んだ文字列を保持するストリングバッファを用意します。
	StringBuffer sb = new StringBuffer();
	// ファイルから読み込んだ一文字を保存する変数です。
	int c;
	// ファイルから１文字ずつ読み込み、バッファへ追加します。
	while ((c = br.read()) != -1) {
	  sb.append((char) c);
	}
	// バッファの内容を文字列化して返します。
	//String source = "*italic* **bold**\n_italic_ __bold__";
	String source = sb.toString();
	println new Markdown().markdown(source)
} catch (FileNotFoundException e) {
	e.printStackTrace();
} catch (IOException e) {
	e.printStackTrace();
}

/**
 * Convert Markdown text into HTML, as per http://daringfireball.net/projects/markdown/ .
 * Usage:
 * <pre><code>
 *     MarkdownProcessor markdown = new MarkdownProcessor();
 *     String html = markdown.markdown("*italic*   **bold**\n_italic_   __bold__");
 * </code></pre>
 */
public class Markdown {
    private Random rnd = new Random();
    private Map<String, LinkDefinition> linkDefinitions = new TreeMap<String, LinkDefinition>();
    private static final CharacterProtector HTML_PROTECTOR = new CharacterProtector();
    private static final CharacterProtector CHAR_PROTECTOR = new CharacterProtector();
    private int listLevel;
    private int tabWidth = 4;

    /**
     * Creates a new Markdown processor.
     */
    public Markdown() {
        listLevel = 0;
    }

    /**
     * Perform the conversion from Markdown to HTML.
     *
     * @param txt - input in markdown format
     * @return HTML block corresponding to txt passed in.
     */
    public String markdown(String text) {
        if (text == null) {
            text = "";
        }

        // 行末を揃える
		// DOSの改行コードをUnixに
        text = text.replaceAll(/(?m)\r\n/, /\n/)
		// Macの改行コードをUnixに
        text = text.replaceAll(/(?m)\r/, /\n/)
		
        // Make sure $text ends with a couple of newlines:
        text += "\n\n"
		
		// Convert tabs to spaces given the default tab width of 4 spaces.
		text = text.replaceAll(/(?m)\t/, "    ")
		text = detabify(text);
		// delete all
        text = text.replaceAll(/(?m)^[ ]+$/, "")
        text = hashHTMLBlocks(text);
        text = stripLinkDefinitions(text);
        text = runBlockGamut(text);
        text = unEscapeSpecialChars(text);
		
		// 空行を削除
        text = text.replaceAll(/(?m)^\s*\n/, "")
        text += "\n"
        return text
    }

    private String encodeBackslashEscapes(String text) {
        char[] normalChars = "`_>!".toCharArray();
        char[] escapedChars = "*{}[]()#+-.".toCharArray();

        // Two backslashes in a row
        text.replaceAll("\\\\\\\\", CHAR_PROTECTOR.encode("\\"));

        // Normal characters don't require a backslash in the regular expression
        encodeEscapes(text, normalChars, "\\\\");
        encodeEscapes(text, escapedChars, "\\\\\\");

        return text;
    }

    private String encodeEscapes(String text, char[] chars, String slashes) {
        for (char ch : chars) {
            String regex = slashes + ch;
            text = text.replaceAll(regex, CHAR_PROTECTOR.encode(String.valueOf(ch)));
        }
        return text;
    }

    private String stripLinkDefinitions(String text) {
        String p = "(?m)^[ ]{0,3}\\[(.+)\\]:" + // ID = $1
                "[ \\t]*\\n?[ \\t]*" + // Space
                "<?(\\S+?)>?" + // URL = $2
                "[ \\t]*\\n?[ \\t]*" + // Space
                "(?:[\"(](.+?)[\")][ \\t]*)?" + // Optional title = $3
                "(?:\\n+|\\Z)";
				
        text = text.replaceAll(p) {
			String id = it[1]
			String url = it[2]
			String title = it[3]
			id = id.toLowerCase()
			url = encodeAmpsAndAngles(url)

			if (title == null) {
				title = "";
			}
			title = title.replaceAll("\"", "&quot;");
			linkDefinitions.put(id, new LinkDefinition(url, title));
			""
        }
		return text
    }

    public String runBlockGamut(String text) {
        text = doHeaders(text);
		text = doHorizontalRules(text);
        text = doLists(text);
        text = doCodeBlocks(text);
        text = doBlockQuotes(text);
		
        text = hashHTMLBlocks(text);
		
        text = formParagraphs(text);
		return text
    }

    private String doHorizontalRules(String text) {
        String[] hrDelimiters = ["\\*", "-", "_"];
        for (String hrDelimiter : hrDelimiters) {
            text = text.replaceAll(/^[ ]{0,2}([ ]?${hrDelimiter}[ ]?){3,}[ ]*$/, "<hr />");
        }
		return text
    }

    public String hashHTMLBlocks(String text) {
        // Hashify HTML blocks:
        // We only want to do this for block-level HTML tags, such as headers,
        // lists, and tables. That's because we still want to wrap <p>s around
        // "paragraphs" that are wrapped in non-block-level tags, such as anchors,
        // phrase emphasis, and spans. The list of tags we're looking for is
        // hard-coded:

        String[] tagsA = [
            "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "table",
            "dl", "ol", "ul", "script", "noscript", "form", "fieldset", "iframe", "math"
        ];
        String[] tagsB = ["ins", "del"];

        String alternationA = tagsA.join("|");
        String alternationB = alternationA + "|" + tagsB.join("|");

        int less_than_tab = tabWidth - 1;

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
        String p1 = "(?m)(" +
                "^<(" + alternationA + ")" +
                "\\b" +
                "(.*\\n)*?" +
                "</\\2>" +
                "[ ]*" +
                "(?=\\n+|\\Z))";

        text = text.replaceAll(p1) {
			String literal = it[0]
			return "\n\n" + HTML_PROTECTOR.encode(literal) + "\n\n"
		}

        // Now match more liberally, simply from `\n<tag>` to `</tag>\n`
        String p2 = "(?m)(" +
                "^" +
                "<(" + alternationB + ")" +
                "\\b" +
                "(.*\\n)*?" +
                ".*</\\2>" +
                "[ ]*" +
                "(?=\\n+|\\Z))";
        text = text.replaceAll(p2) {
			String literal = it[0]
			return "\n\n" + HTML_PROTECTOR.encode(literal) + "\n\n"
		}

        // Special case for <hr>
        String p3 = "(?m)(?:" +
                "(?<=\\n\\n)" +
                "|" +
                "\\A\\n?" +
                ")" +
                "(" +
                "[ ]{0," + less_than_tab + "}" +
                "<(hr)" +
                "\\b" +
                "([^<>])*?" +
                "/?>" +
                "[ ]*" +
                "(?=\\n{2,}|\\Z))";
        text = text.replaceAll(p3) {
			String literal = it[0]
			"\n\n" + HTML_PROTECTOR.encode(literal) + "\n\n"
		}

        // Special case for standalone HTML comments:
        String p4 = "(?m)(?:" +
                "(?<=\\n\\n)" +
                "|" +
                "\\A\\n?" +
                ")" +
                "(" +
                "[ ]{0," + less_than_tab + "}" +
                "(?s:" +
                "<!" +
                "(--.*?--\\s*)+" +
                ">" +
                ")" +
                "[ ]*" +
                "(?=\\n{2,}|\\Z)" +
                ")";
        text = text.replaceAll(p4) {
			String literal = it[0]
			return "\n\n" + HTML_PROTECTOR.encode(literal) + "\n\n"
		}
		return text
    }

    private String formParagraphs(String markup) {
        markup = markup.replaceAll(/\A\n+/, "")
        markup = markup.replaceAll(/\n+\z/, "")

        def paragraphs = []
		boolean isEmpty =  markup.length() == 0
        if (isEmpty) {
            paragraphs = [];
        } else {
            paragraphs = markup.split(/\n{2,}/)
        }
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i];
            String decoded = HTML_PROTECTOR.decode(paragraph);
            if (decoded != null) {
                paragraphs[i] = decoded;
            } else {
                paragraph = runSpanGamut(paragraph)
                paragraphs[i] = "<p>" + paragraph + "</p>";
            }
        }
        return paragraphs.join("\n\n")
    }


    private String doAutoLinks(String markup) {
        markup = markup.replaceAll("<((https?|ftp):[^'\">\\s]+)>", "<a href=\"\$1\">\$1</a>");
        String email = "<([-.\\w]+\\@[-a-z0-9]+(\\.[-a-z0-9]+)*\\.[a-z]+)>";
        markup = markup.replaceAll(email) { all, address ->
			address = unEscapeSpecialChars(address);
			String addr = encodeEmail(address);
			String url = encodeEmail("mailto:" + address);
			return "<a href=\"" + url + "\">" + addr + "</a>";
        }
        return markup;
    }

    private String unEscapeSpecialChars(String ed) {
        for (String hash : CHAR_PROTECTOR.getAllEncodedTokens()) {
            String plaintext = CHAR_PROTECTOR.decode(hash);
            ed = ed.replaceAll(hash, plaintext);
        }
		return ed
    }

    private String encodeEmail(String s) {
        StringBuffer sb = new StringBuffer();
        char[] email = s.toCharArray();
        for (char ch : email) {
            double r = rnd.nextDouble();
            if (r < 0.45) {      // Decimal
                sb.append("&#");
                sb.append((int) ch);
                sb.append(';');
            } else if (r < 0.9) {  // Hex
                sb.append("&#x");
                sb.append(Integer.toString((int) ch, 16));
                sb.append(';');
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private String doBlockQuotes(String markup) {
        String p = "(" +
                "(" +
                "^[ \t]*>[ \t]?" + // > at the start of a line
                ".+\\n" + // rest of the first line
                "(.+\\n)*" + // subsequent consecutive lines
                "\\n*" + // blanks
                ")+" +
                ")";
        return markup.replaceAll(p ) { all, blockQuote ->
                blockQuote = blockQuote.replaceAll("^[ \t]*>[ \t]?", "");
                blockQuote = blockQuote.replaceAll(/^[ \t]+$/, "");
                blockQuote = blockQuote = runBlockGamut(blockQuote);
                blockQuote = blockQuote.replaceAll("^", "  ");

                String p1 = "(?s)(\\s*<pre>.*?</pre>)";
                blockQuote = blockQuote.replaceAll(p1) { all1, pre ->
					return pre.replaceAll("^  ", "");
                }
                return "<blockquote>\n${blockQuote}\n</blockquote>\n\n";
        }
    }

    private String doCodeBlocks(String markup) {
        Pattern p = Pattern.compile("" +
                "(?:\\n\\n|\\A)" +
                "((?:" +
                "(?:[ ]{4})" +
                ".*\\n+" +
                ")+" +
                ")" +
                "((?=^[ ]{0,4}\\S)|\\Z)", Pattern.MULTILINE);
        return markup.replaceAll(p) {
			String LANG_IDENTIFIER = "lang:";
			String codeBlock = it[1]
			codeBlock = outdent(codeBlock);
			codeBlock = encodeCode(codeBlock);
			codeBlock = detabify(codeBlock);
			codeBlock = codeBlock.replaceAll("\\A\\n+","").replaceAll("\\s+\\z","");
			if ( codeBlock == null ) {
				codeBlock = ""
			} else {
				String[] splitted = codeBlock.split("\\n");
				codeBlock = splitted[0];
			}
			String out = "";
			String firstLine = codeBlock
			boolean isLanguageIdentifier = false;
			if (firstLine== null) {
				isLanguageIdentifier = false;
			} else {
				String lang = "";
				if (firstLine.startsWith(LANG_IDENTIFIER)) {
					lang = firstLine.replaceFirst(LANG_IDENTIFIER, "").trim();
				}
				isLanguageIdentifier =lang.length() > 0
			}
			
			if (isLanguageIdentifier) {
				// dont'use %n in format string (markdown aspect every new line char as "\n")
				//String codeBlockTemplate = "<pre class=\"brush: %s\">%n%s%n</pre>"; // http://alexgorbatchev.com/wiki/SyntaxHighlighter
				String codeBlockTemplate = "\n\n<pre class=\"%s\">\n%s\n</pre>\n\n"; // http://shjs.sourceforge.net/doc/documentation.html
				String lang = firstLine.replaceFirst(LANG_IDENTIFIER, "").trim();
				String block = codeBlock.replaceFirst( firstLine+"\n", "");
				out =  String.format(codeBlockTemplate, lang, block);
			} else {
				// dont'use %n in format string (markdown aspect every new line char as "\n")
				String codeBlockTemplate = "\n\n<pre><code>%s\n</code></pre>\n\n";
				out = String.format(codeBlockTemplate, codeBlock);
			}
			return out;
        }
    }

    private String encodeCode(String ed) {
        ed = ed.replaceAll("&", "&amp;");
        ed = ed.replaceAll("<", "&lt;");
        ed = ed.replaceAll(">", "&gt;");
        ed = ed.replaceAll("\\*", CHAR_PROTECTOR.encode("*"));
        ed = ed.replaceAll("_", CHAR_PROTECTOR.encode("_"));
        ed = ed.replaceAll("\\{", CHAR_PROTECTOR.encode("{"));
        ed = ed.replaceAll("\\}", CHAR_PROTECTOR.encode("}"));
        ed = ed.replaceAll("\\[", CHAR_PROTECTOR.encode("["));
        ed = ed.replaceAll("\\]", CHAR_PROTECTOR.encode("]"));
        ed = ed.replaceAll("\\\\", CHAR_PROTECTOR.encode("\\"));
		return ed
    }

    private String doLists(String text) {
        int lessThanTab = tabWidth - 1;

        String wholeList =
                "(" +
                  "(" +
                    "[ ]{0," + lessThanTab + "}" +
                    "((?:[-+*]|\\d+[.]))" + // $3 is first list item marker
                    "[ ]+" +
                  ")" +
                  "(?s:.+?)" +
                  "(" +
                    "\\z" + // End of input is OK
                    "|" +
                    "\\n{2,}" +
                    "(?=\\S)" + // If not end of input, then a new para
                    "(?![ ]*" +
                      "(?:[-+*]|\\d+[.])" +
                      "[ ]+" +
                    ")" + // negative lookahead for another list marker
                  ")" +
                ")";

        if (listLevel > 0) {
            String matchStartOfLine = "^" + wholeList;
            text = text.replaceAll(matchStartOfLine) { all, list, second, listStart ->
				String listType = "";

				if (listStart ==~ /[*+-]/) {
				 listType = "ul";
				} else {
				 listType = "ol";
				}

				// Turn double returns into triple returns, so that we can make a
				// paragraph for the last item in a list, if necessary:
				list = list.replaceAll( "\\n{2,}", "\n\n\n");

				String result = processListItems(list);

				// Trim any trailing whitespace, to put the closing `</ol>` or `</ul>`
				// up on the preceding line, to get it past the current stupid
				// HTML block parser. This is a hack to work around the terrible
				// hack that is the HTML block parser.
				result = result.replaceAll(/\\s+$/, "");

				String html;
				if ("ul".equals(listType)) {
					html = "<ul>" + result + "</ul>\n";
				} else {
					html = "<ol>" + result + "</ol>\n";
				}
				return html;
            }
        } else {
            String matchStartOfLine = /(?m)(?:(?<=\n\n)|\A\n?)/ + wholeList;
            text = text.replaceAll(matchStartOfLine){
				// Turn double returns into triple returns, so that we can make a
				// paragraph for the last item in a list, if necessary:
				String list = it[1]
				String listStart = it[3]
				list = ((String)list).replaceAll( "\n{2,}", "\n\n\n");

				String result = processListItems(list);

				String html;
				if (listStart ==~ /[*+-]/ ) {
					html = "<ul>\n" + result + "</ul>\n";
				} else {
					html = "<ol>\n" + result + "</ol>\n";
				}
				return html;
			}
        }

        return text;
    }

    private String processListItems(String list) {
        // The listLevel variable keeps track of when we're inside a list.
        // Each time we enter a list, we increment it; when we leave a list,
        // we decrement. If it's zero, we're not in a list anymore.
        //
        // We do this because when we're not inside a list, we want to treat
        // something like this:
        //
        //       I recommend upgrading to version
        //       8. Oops, now this line is treated
        //       as a sub-list.
        //
        // As a single paragraph, despite the fact that the second line starts
        // with a digit-period-space sequence.
        //
        // Whereas when we're inside a list (or sub-list), that line will be
        // treated as the start of a sub-list. What a kludge, huh? This is
        // an aspect of Markdown's syntax that's hard to parse perfectly
        // without resorting to mind-reading. Perhaps the solution is to
        // change the syntax rules such that sub-lists must start with a
        // starting cardinal number; e.g. "1." or "a.".
        listLevel++;

        // Trim trailing blank lines:
        list = list.replaceAll("\\n{2,}\\z", "\n");

        String p = "(\\n)?" +
                "^([ \\t]*)([-+*]|\\d+[.])[ ]+" +
                "((?s:.+?)(\\n{1,2}))" +
                "(?=\\n*(\\z|\\2([-+\\*]|\\d+[.])[ \\t]+))";
        list = list.replaceAll(p) {
			String leadingLine = it[1]
			String item = it[4]
			if (!isEmptyString(leadingLine) || hasParagraphBreak(item)) {
				item = runBlockGamut(outdent(item));
			} else {
				// Recurse sub-lists
				item = doLists(outdent(item));
				item = runSpanGamut(item);
			}
			return "<li>" + item.trim() + "</li>\n";
        }
        listLevel--;
        return list;
    }

    private boolean hasParagraphBreak(String item) {
        return item.contains("\n\n")
    }

    private boolean isEmptyString(String leadingLine) {
        return leadingLine == null || leadingLine.equals("");
    }

    private String doHeaders(String markup) {
        // setext-style headers
        //markup.replaceAll(/(?m)^(.*)\n====+$/, '<h1>$1</h1>');
        markup = markup.replaceAll(/(?m)^(.*)\n====+$/, '<h1>$1</h1>');
        markup = markup.replaceAll(/(?m)^(.*)\n----+$/, '<h2>$1</h2>');

        // atx-style headers - e.g., "#### heading 4 ####"
        String p = /(?m)^(#{1,6})\s*(.*?)\s*\1$/
        markup = markup.replaceAll(p) {
			String marker = it[1]
			String heading = it[2]
            int level = marker.length()
            String tag = "h${level}"
            return "<${tag}>${heading}</${tag}>\n"
        }
        return markup;
    }

    public String runSpanGamut(String text) {
        text = escapeSpecialCharsWithinTagAttributes(text);
        text = doCodeSpans(text);
        text = encodeBackslashEscapes(text);

        text = doImages(text);
        text = doAnchors(text);
        text = doAutoLinks(text);

        // Fix for BUG #1357582
        // We must call escapeSpecialCharsWithinTagAttributes() a second time to
        // escape the contents of any attributes generated by the prior methods.
        // - Nathan Winant, nw@exegetic.net, 8/29/2006
        text = escapeSpecialCharsWithinTagAttributes(text);

        text = encodeAmpsAndAngles(text);
        text = doItalicsAndBold(text);

        // Manual line breaks
        text = text.replaceAll(" {2,}\n", " <br />\n");
        return text;
    }

    /**
     * escape special characters
     *
     * Within tags -- meaning between < and > -- encode [\ ` * _] so they
     * don't conflict with their use in Markdown for code, italics and strong.
     * We're replacing each such character with its corresponding random string
     * value; this is likely overkill, but it should prevent us from colliding
     * with the escape values by accident.
     *
     * @param text
     * @return
     */
    private String escapeSpecialCharsWithinTagAttributes(String text) {
        Collection<HTMLToken> tokens = tokenizeHTML(text)
        String newText = "";

        for (HTMLToken token : tokens) {
            String value = "";
            value = token.getText();
            if (token.isTag()) {
                value = value.replaceAll("\\\\", CHAR_PROTECTOR.encode("\\"));
                value = value.replaceAll("`", CHAR_PROTECTOR.encode("`"));
                value = value.replaceAll("\\*", CHAR_PROTECTOR.encode("*"));
                value = value.replaceAll("_", CHAR_PROTECTOR.encode("_"));
            }
            newText += value
        }

        return newText
    }

    private String doImages(String text) {
        text = text.replaceAll("!\\[(.*)\\]\\((.*) \"(.*)\"\\)", "<img src=\"\$2\" alt=\"\$1\" title=\"\$3\" />");
        text = text.replaceAll("!\\[(.*)\\]\\((.*)\\)", "<img src=\"\$2\" alt=\"\$1\" />");
		return text
    }

    private String doAnchors(String markup) {
        // Internal references: [link text] [id]
        String internalLink = "(" +
                "\\[(.*?)\\]" + // Link text = $2
                "[ ]?(?:\\n[ ]*)?" +
                "\\[(.*?)\\]" + // ID = $3
                ")";
        markup = markup.replaceAll(internalLink) {
			String replacementText = ""
			String wholeMatch = it[1]
			String linkText = it[2]
			String id = it[3]
			id = id.toLowerCase()
			if (id == null || "".equals(id)) { // for shortcut links like [this][]
				id = linkText.toLowerCase();
			}

			LinkDefinition defn = linkDefinitions.get(id);
			if (defn != null) {
				String url = defn.getUrl();
				// protect emphasis (* and _) within urls
				url = url.replaceAll("\\*", CHAR_PROTECTOR.encode("*"));
				url = url.replaceAll("_", CHAR_PROTECTOR.encode("_"));
				String title = defn.getTitle();
				String titleTag = "";
				if (title != null && !title.equals("")) {
					// protect emphasis (* and _) within urls
					title = title.replaceAll("\\*", CHAR_PROTECTOR.encode("*"));
					title = title.replaceAll("_", CHAR_PROTECTOR.encode("_"));
					titleTag = " title=\"" + title + "\"";
				}
				replacementText = "<a href=\"" + url + "\"" + titleTag + ">" + linkText + "</a>";
			} else {
				replacementText = wholeMatch;
			}
			return replacementText;
        }

        // Inline-style links: [link text](url "optional title")
        String inlineLink = "(?m)(" + // Whole match = $1
                "\\[(.*?)\\]" + // Link text = $2
                "\\(" +
                "[ \\t]*" +
                "<?(.*?)>?" + // href = $3
                "[ \\t]*" +
                "(" +
                "(['\"])" + // Quote character = $5
                "(.*?)" + // Title = $6
                "\\5" +
                ")?" +
                "\\)" +
                ")";
        markup = markup.replaceAll(inlineLink ) {
			// protect emphasis (* and _) within urls
			String linkText = it[2]
			String url = it[3]
			String title = it[6]
			url = url.replaceAll("\\*", CHAR_PROTECTOR.encode("*"));
			url = url.replaceAll("_", CHAR_PROTECTOR.encode("_"));
			StringBuffer result = new StringBuffer();
			result.append("<a href=\"").append(url).append("\"");
			if (title != null) {
				// protect emphasis (* and _) within urls
				title = title.replaceAll("\\*", CHAR_PROTECTOR.encode("*"));
				title = title.replaceAll("_", CHAR_PROTECTOR.encode("_"));
				title = title.replaceAll(/"/, /'/);
				result.append(" title=\"");
				result.append(title);
				result.append("\"");
			}
			result.append(">").append(linkText);
			result.append("</a>");
			return result.toString();
        }
		
        // Last, handle reference-style shortcuts: [link text]
        // These must come last in case you've also got [link test][1]
        // or [link test](/foo)
        String referenceShortcut = "(?m)(" + // wrap whole match in $1
                                                        "\\[" +
                                                        "([^\\[\\]]+)" + // link text = $2; can't contain '[' or ']'
                                                        "\\]" +
                                                    ")";
        markup = markup.replaceAll(referenceShortcut) { all, wholeMatch, linkText ->
			String replacementText;
			String id = linkText.toLowerCase(); // link id should be lowercase
			id = id.replaceAll("[ ]?\\n", " "); // change embedded newlines into spaces

			LinkDefinition defn = linkDefinitions.get(id.toLowerCase());
			if (defn != null) {
				String url = defn.getUrl();
				// protect emphasis (* and _) within urls
				url = url.replaceAll("\\*", CHAR_PROTECTOR.encode("*"));
				url = url.replaceAll("_", CHAR_PROTECTOR.encode("_"));
				String title = defn.getTitle();
				String titleTag = "";
				if (title != null && !title.equals("")) {
					// protect emphasis (* and _) within urls
					title = title.replaceAll("\\*", CHAR_PROTECTOR.encode("*"));
					title = title.replaceAll("_", CHAR_PROTECTOR.encode("_"));
					titleTag = " title=\"" + title + "\"";
				}
				replacementText = "<a href=\"" + url + "\"" + titleTag + ">" + linkText + "</a>";
			} else {
				replacementText = wholeMatch;
			}
			return replacementText;
        }

        return markup;
    }

    private String doItalicsAndBold(String markup) {
        markup = markup.replaceAll(/(\*\*|__)(?=\S)(.+?[*_]*)(?<=\S)\1/, '<strong>$2</strong>');
        markup = markup.replaceAll(/(\*|_)(?=\S)(.+?)(?<=\S)\1/, '<em>$2</em>');
        return markup;
    }

    private String encodeAmpsAndAngles(String markup) {
        // Ampersand-encoding based entirely on Nat Irons's Amputator MT plugin:
        // http://bumppo.net/projects/amputator/
        markup.replaceAll("&(?!#?[xX]?(?:[0-9a-fA-F]+|\\w+);)", "&amp;");
        markup.replaceAll(/<(?![a-z\/?\\$!])/, "&lt;");
        return markup
    }

    private String doCodeSpans(String markup) {
            return markup.replaceAll("(?<!\\\\)(`+)(.+?)(?<!`)\\1(?!`)") {
				String code = it[2]
				code = code.replaceAll("^[ \\t]+", "").replaceAll(/[ \\t]+$/, "")
				code = encodeCode( code )
				"<code>${code}</code>"
			}
    }

	/**
	 * Remove a number of spaces at the start of each line.
	 * @param spaces
	 * @return
	 */
	private String outdent(String text, int spaces) {
		text = text.replaceAll("^(\\t|[ ]{1," + spaces + "})", "")
		return text
	}
	
	/**
	 * Remove one tab width (4 spaces) from the start of each line.
	 * @return
	 */
	private String outdent(String text) {
		text = text.replaceAll("^(\\t|[ ]{1,4})", "")
		return text
	}

	/**
	 * Convert tabs to spaces given the default tab width of 4 spaces.
	 *
	 * @param text
	 * @return
	 */
	public String detabify(String text) {
		def tabWidth = 4;
		return text.replaceAll(/\t/, ' ' * tabWidth)
	}
	
	/**
	 * Parse HTML tags, returning a Collection of HTMLToken objects.
	 * @return
	 */
	private Collection<HTMLToken> tokenizeHTML( String text ) {
		List<HTMLToken> tokens = new ArrayList<HTMLToken>();
		String nestedTags = nestedTagsRegex(6);

		String p = "" +
				"(?i)(?s:<!(--.*?--\\s*)+>)" +
				"|" +
				"(?i)(?s:<\\?.*?\\?>)" +
				"|" +
				"(?i)" + nestedTags +
				"";
		
		//def m = text =~ p
		int lastPos = 0;
		Matcher m = (text =~ p)
		while(m.find()) {
			if (lastPos < m.start()) {
				tokens.add(HTMLToken.text(text.substring(lastPos, m.start())))
			}
			tokens.add(HTMLToken.tag(text.substring(m.start(), m.end())))
			lastPos = m.end()
		}
		if (lastPos < text.length()) {
			tokens.add(HTMLToken.text(text.substring(lastPos, text.length())));
		}
		return tokens;
	}
	
	/**
	 * Regex to match a tag, possibly with nested tags such as <a href="<MTFoo>">.
	 *
	 * @param depth - How many levels of tags-within-tags to allow.  The example <a href="<MTFoo>"> has depth 2.
	 */
	private String nestedTagsRegex(int depth) {
		if (depth == 0) {
			return "";
		} else {
			return "(?:<[a-z/!\$](?:[^<>]|" + nestedTagsRegex(depth - 1) + ")*>)";
		}
	}

    @Override
    public String toString() {
        return "Markdown Processor for Java 0.4.0 (compatible with Markdown 1.0.2b2)";
    }
}

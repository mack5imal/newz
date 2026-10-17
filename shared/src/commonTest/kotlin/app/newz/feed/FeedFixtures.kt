package app.newz.feed

/** Feed fixtures as string constants (commonTest has no portable resource loading). */
internal object FeedFixtures {
    val RSS2_BASIC =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:content="http://purl.org/rss/1.0/modules/content/">
          <channel>
            <title>Publisher &amp; Co News</title>
            <link>https://publisher.example/</link>
            <description>Test feed</description>
            <item>
              <title>AT&amp;T buys a thing</title>
              <link>https://publisher.example/att</link>
              <guid isPermaLink="false">att-1</guid>
              <pubDate>Fri, 16 Oct 2026 09:30:00 GMT</pubDate>
              <description>&lt;p&gt;Body &lt;b&gt;bold&lt;/b&gt; text&lt;/p&gt;</description>
              <media:content url="https://cdn.publisher.example/att.jpg" medium="image" />
            </item>
            <item>
              <title>It&#8217;s here</title>
              <link>https://publisher.example/its-here</link>
              <pubDate>Thu, 15 Oct 2026 22:00:00 -0400</pubDate>
              <description>No picture here.</description>
            </item>
            <item>
              <title><![CDATA[CDATA <em>title</em> & more]]></title>
              <link>https://publisher.example/cdata</link>
              <description><![CDATA[<p>Watch:</p><iframe src="https://www.youtube.com/embed/abc"></iframe>]]></description>
            </item>
            <item>
              <title>Image only on Google</title>
              <link>https://publisher.example/google-image</link>
              <pubDate>Thu, 15 Oct 2026 08:00:00 +0000</pubDate>
              <enclosure url="https://lh3.googleusercontent.com/x.jpg" length="100" type="image/jpeg" />
            </item>
            <item>
              <title>Image in content</title>
              <link>https://publisher.example/img-in-content</link>
              <pubDate>Thu, 15 Oct 2026 07:00:00 +0000</pubDate>
              <content:encoded><![CDATA[<p><img src="https://cdn.publisher.example/inline.png" alt=""> text</p>]]></content:encoded>
            </item>
          </channel>
        </rss>
        """.trimIndent()

    val ATOM =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Atom Example</title>
          <link href="https://atom.example/"/>
          <updated>2026-10-16T10:00:00Z</updated>
          <id>urn:uuid:feed</id>
          <entry>
            <title>Atom entry one</title>
            <link href="https://atom.example/one"/>
            <id>urn:uuid:one</id>
            <published>2026-10-16T09:00:00+02:00</published>
            <updated>2026-10-16T09:00:00+02:00</updated>
            <summary>First entry summary</summary>
          </entry>
        </feed>
        """.trimIndent()

    val RDF =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                 xmlns="http://purl.org/rss/1.0/"
                 xmlns:dc="http://purl.org/dc/elements/1.1/">
          <channel rdf:about="https://rdf.example/">
            <title>RDF Example</title>
            <link>https://rdf.example/</link>
            <description>RSS 1.0 feed</description>
            <items>
              <rdf:Seq>
                <rdf:li rdf:resource="https://rdf.example/a"/>
              </rdf:Seq>
            </items>
          </channel>
          <item rdf:about="https://rdf.example/a">
            <title>RDF item A</title>
            <link>https://rdf.example/a</link>
            <description>About A</description>
            <dc:date>2026-10-16T08:00:00Z</dc:date>
          </item>
        </rdf:RDF>
        """.trimIndent()

    /** Declared ISO-8859-1; "Café" is written with the single byte 0xE9. */
    val LATIN1: ByteArray =
        bytes(
            "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<rss version=\"2.0\"><channel><title>Latin</title>" +
                "<link>https://latin.example/</link><item><title>Caf",
            0xE9,
            " ouvert</title><link>https://latin.example/cafe</link></item></channel></rss>",
        )

    /** Declared windows-1252; 0x92 is a right single quote, 0x93/0x94 curly double quotes. */
    val CP1252: ByteArray =
        bytes(
            "<?xml version=\"1.0\" encoding=\"windows-1252\"?>\n<rss version=\"2.0\"><channel><title>Quotes</title>" +
                "<link>https://quotes.example/</link><item><title>It",
            0x92,
            "s ",
            0x93,
            "quoted",
            0x94,
            "</title><link>https://quotes.example/q</link></item></channel></rss>",
        )

    /** No prolog encoding: the HTTP header charset applies. */
    val HEADER_LATIN1: ByteArray =
        bytes(
            "<rss version=\"2.0\"><channel><title>H</title><link>https://h.example/</link><item><title>Na",
            0xEF,
            "ve</title><link>https://h.example/n</link></item></channel></rss>",
        )

    private fun bytes(vararg parts: Any): ByteArray =
        parts
            .flatMap { part ->
                when (part) {
                    is String -> part.encodeToByteArray().toList()
                    is Int -> listOf(part.toByte())
                    else -> error("unsupported part")
                }
            }.toByteArray()
}

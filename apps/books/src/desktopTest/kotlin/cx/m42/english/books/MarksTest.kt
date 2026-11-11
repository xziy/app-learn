package cx.m42.english.books

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarksTest {
    private val sentence = Sentence(
        ours = "Я не знаю своего имени, но мне дали личный номер 10-53.",
        thought = "Я [делаю] не знать моё имя, но я был дан [какой-то] личный номер: 10-53.",
        english = "I do not know my name, but I was given a personal number: 10-53.",
        transcription = "[ай ду: нот ноу май нэйм бат ай wоз г**и**вн э п**œ**:снл н**а**мбэ тэн ф**и**фти θри:]",
        nouns = listOf(
            Noun("name", "имя", form = "name", thought = "имя", ours = "имени"),
            Noun("number", "номер", form = "number", thought = "номер", ours = "номер", sense = "label"),
        ),
    )

    @Test
    fun eachNounIsFoundInEachLineByItsOwnSpelling() {
        val english = spans(sentence.english, sentence.nouns, Line.English)
        assertEquals(listOf("name", "number"), english.map { sentence.english.substring(it.start, it.end) })
        assertEquals(listOf(0, 1), english.map { it.noun })
        val thought = spans(sentence.thought, sentence.nouns, Line.Thought)
        assertEquals(listOf("имя", "номер"), thought.map { sentence.thought.substring(it.start, it.end) })
        val ours = spans(sentence.ours, sentence.nouns, Line.Ours)
        assertEquals(listOf("имени", "номер"), ours.map { sentence.ours.substring(it.start, it.end) })
    }

    @Test
    fun aWordIsFoundWholeAndInAnyCase() {
        val nouns = listOf(Noun("car", "машина", form = "car", thought = "машина"))
        assertEquals(listOf(10), spans("A cartoon car.", nouns, Line.English).map { it.start })
        assertEquals(listOf(0), spans("Машина, машинально.", nouns, Line.Thought).map { it.start })
        assertTrue(spans("Cartoons only.", nouns, Line.English).isEmpty())
    }

    @Test
    fun theSameWordTwiceIsTheFirstPlaceAndTheSecond() {
        val text = "The man saw the other man."
        val nouns = listOf(Noun("man", "мужчина", form = "man"), Noun("man", "мужчина", form = "man"))
        assertEquals(listOf(4 to 0, 22 to 1), spans(text, nouns, Line.English).map { it.start to it.noun })
    }

    @Test
    fun aNounThatIsNotThereIsNotSetOffAndALineWithoutItsSpellingHasNone() {
        val nouns = listOf(Noun("dog", "собака", form = "dog"), Noun("cat", "кошка", form = "cat"))
        assertEquals(listOf(1), spans("A cat.", nouns, Line.English).map { it.noun })
        assertTrue(spans("Кошка.", nouns, Line.Ours).isEmpty())
    }

    @Test
    fun theTranscriptionHasTheNounsByTheirWords() {
        // Digits in the English: the transcription says them in more words, so nothing lines up.
        val english = spans(sentence.english, sentence.nouns, Line.English)
        assertTrue(soundNouns(sentence.english, sentence.transcription, english).isEmpty())
        val plain = Sentence(
            ours = "Я знаю его имя.",
            english = "I know his name.",
            transcription = "[ай ноу хиз нэйм]",
            nouns = listOf(Noun("name", "имя", form = "name")),
        )
        val spans = spans(plain.english, plain.nouns, Line.English)
        assertEquals(mapOf(3 to 0), soundNouns(plain.english, plain.transcription, spans))
        assertEquals("[нэйм]", soundOf(plain, 0))
        assertNull(soundOf(sentence, 0))
    }

    @Test
    fun theStressIsTakenOutOfAWord() {
        assertEquals(
            listOf(Sound("г", false), Sound("и", true), Sound("вн", false)),
            sounds("г**и**вн"),
        )
        assertEquals(listOf(Sound("нэйм", false)), sounds("нэйм"))
    }

    @Test
    fun russianCountsHaveTheirThreeForms() {
        assertEquals("1 фраза", ruCount(1, "фраза", "фразы", "фраз"))
        assertEquals("3 фразы", ruCount(3, "фраза", "фразы", "фраз"))
        assertEquals("11 фраз", ruCount(11, "фраза", "фразы", "фраз"))
        assertEquals("341 фраза", ruCount(341, "фраза", "фразы", "фраз"))
        assertEquals("25 фраз", ruCount(25, "фраза", "фразы", "фраз"))
    }

    @Test
    fun aParagraphRunsOnWithItsSentencesAndTheirNouns() {
        val paragraph = Paragraph(
            1,
            listOf(
                Sentence("Раз.", english = "One dog.", nouns = listOf(Noun("dog", "собака", form = "dog"))),
                Sentence("Два кота."),
            ),
        )
        val built = buildFlow(paragraph, Line.English, Paper.Sepia.colors(), ReaderStyle(ReaderSettings(), Paper.Sepia.colors()), true, emptySet())
        // The second sentence is not put the English way yet: its Russian stands in for it.
        assertEquals("One dog. Два кота.", built.text.text)
        assertEquals(listOf(NounHit(4, 7, 0, 0)), built.hits)
        assertEquals(0, built.sentenceAt(2))
        assertEquals(1, built.sentenceAt(12))
    }
}

class BookFormatTest {
    private val json = """
        {"book":1,"title":"Лето у моря","author":"Кто-то","transcription":false,"sentences":3,"translated":2,
         "paragraphs":[
           {"n":1,"heading":true,"sentences":[{"ours":"Глава первая.","thought":"Глава один.","english":"Chapter one.","transcription":"",
             "nouns":[{"word":"chapter","meaning":"глава","form":"Chapter","thought":"Глава","ours":"Глава"}]}]},
           {"n":2,"sentences":[
             {"ours":"Мы поехали на море.","thought":"Мы поехали к [известному] морю.","english":"We went to the sea.","transcription":"",
              "nouns":[{"word":"sea","meaning":"море","form":"sea","thought":"морю","ours":"море"}]},
             {"ours":"Там было тепло.","thought":"","english":"","transcription":"","nouns":[]}]}],
         "words":{"sea":{"transcription":"[си:]","countable":"both","plural":[{"word":"seas","transcription":"[си:з]"}],
                  "senses":[{"ru":["море"],"en":"salt water"},{"id":"lots","ru":["масса"]}],"notes":"At sea — в море."}}}
    """.trimIndent()

    @Test
    fun aBookIsReadAsTheSkillWritesIt() {
        val book = parseBook("summer", json)
        assertEquals("Лето у моря", book.title)
        assertEquals("Кто-то", book.author)
        assertEquals(false, book.transcription)
        assertEquals(3, book.sentences)
        assertEquals(2, book.translated)
        assertEquals(listOf(0), book.headings)
        val sea = book.paragraphs[1].sentences[0].nouns.single()
        assertEquals(Noun("sea", "море", form = "sea", thought = "морю", ours = "море"), sea)
        assertEquals("n:sea", sea.key)
        val word = book.words.getValue("sea")
        assertEquals("[си:]", word.transcription)
        assertEquals(listOf(WordSense(null, listOf("море"), "salt water"), WordSense("lots", listOf("масса"))), word.senses)
        assertEquals(listOf(WordForm("seas", "[си:з]")), word.plural)
        assertEquals(mapOf("n:chapter" to 1, "n:sea" to 1), book.counts)
    }

    @Test
    fun whatIsMissingHasItsDefaults() {
        val book = parseBook("bare", """{"paragraphs":[{"sentences":[{"ours":"Одна."}]},{"sentences":[]}]}""")
        assertEquals("bare", book.title)
        assertEquals(true, book.transcription)
        assertEquals(1, book.paragraphs.size)
        assertEquals(0, book.translated)
    }

    @Test
    fun theShelfIsReadFromItsIndex() {
        val index = parseIndex("""[{"slug":"a","title":"A","paragraphs":10,"sentences":20,"translated":5},{"title":"no slug"}]""")
        assertEquals(listOf(BookInfo("a", "A", "", 10, 20, 5)), index)
    }

    @Test
    fun aPlaceIsKeptAsText() {
        assertEquals(Place(12, 40), Place.decode(Place(12, 40).encode()))
        assertEquals(Place(3, 0), Place.decode("3"))
        assertNull(Place.decode("x:1"))
        assertNull(Place.decode(null))
        assertEquals(50, percentRead(Place(6), 10))
        assertEquals(0, percentRead(Place(0), 10))
        assertEquals(100, percentRead(Place(11), 10))
        assertNull(percentRead(null, 10))
    }

    @Test
    fun theLastLineStaysOnAndABookWithoutTranscriptionHasNone() {
        val one = ReaderSettings(lines = setOf(Line.English))
        assertEquals(one, one.toggle(Line.English))
        val book = parseBook("summer", json)
        assertEquals(listOf(Line.English), ReaderSettings(lines = setOf(Line.Transcription)).shown(book))
        assertEquals(listOf(Line.Ours, Line.English), ReaderSettings(lines = setOf(Line.English, Line.Transcription, Line.Ours)).shown(book))
    }
}

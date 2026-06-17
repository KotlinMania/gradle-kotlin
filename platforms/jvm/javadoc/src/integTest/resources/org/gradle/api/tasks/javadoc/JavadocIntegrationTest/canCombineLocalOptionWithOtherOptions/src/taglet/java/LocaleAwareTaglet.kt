import com.sun.javadoc.Tag
import com.sun.tools.doclets.Taglet

class LocaleAwareTaglet : Taglet {
    fun inField(): Boolean {
        return false
    }

    fun inConstructor(): Boolean {
        return false
    }

    fun inMethod(): Boolean {
        return false
    }

    fun inOverview(): Boolean {
        return true
    }

    fun inPackage(): Boolean {
        return false
    }

    fun inType(): Boolean {
        return true
    }

    val isInlineTag: Boolean
        get() = false

    val name: String
        get() = "LOCALE_AWARE"

    fun toString(tag: Tag?): String {
        return "<B>USED LOCALE=" + java.util.Locale.getDefault() + "</B>\n"
    }

    fun toString(tags: Array<Tag?>): String {
        return toString(tags[0])
    }

    companion object {
        fun register(tagletMap: MutableMap<*, *>) {
            val taglet = LocaleAwareTaglet()
            tagletMap.put(taglet.name, taglet)
        }
    }
}

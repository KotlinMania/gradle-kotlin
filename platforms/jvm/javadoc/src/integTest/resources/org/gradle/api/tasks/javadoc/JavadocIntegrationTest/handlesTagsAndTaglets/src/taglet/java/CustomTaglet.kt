import com.sun.tools.doclets.Taglet

class CustomTaglet : Taglet {
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
        return false
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
        get() = "customtaglet"

    fun toString(tag: Tag): String {
        return "<DT><B>Custom Taglet:</B></DT>\n<DD>" + tag.text() + "</DD>\n"
    }

    fun toString(tags: Array<Tag?>): String {
        return toString(tags[0])
    }

    companion object {
        fun register(tagletMap: MutableMap<*, *>) {
            val taglet = CustomTaglet()
            tagletMap.put(taglet.name, taglet)
        }
    }
}

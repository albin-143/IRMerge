package com.example.irmerge

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.text.InputType
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private var ir: ConsumerIrManager? = null
    private lateinit var list: LinearLayout
    private val prefs by lazy { getSharedPreferences("ir", Context.MODE_PRIVATE) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        root.addView(Button(this).apply { text = "+ Add button"; setOnClickListener { showAdd() } })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) })
        setContentView(root)
        if (ir?.hasIrEmitter() != true) toast("This phone has no IR blaster")
        render()
    }

    private fun load(): JSONArray = JSONArray(prefs.getString("items", "[]"))
    private fun save(a: JSONArray) = prefs.edit().putString("items", a.toString()).apply()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun render() {
        list.removeAllViews()
        val arr = load()
        val groups = LinkedHashMap<String, MutableList<Int>>()
        for (i in 0 until arr.length()) groups.getOrPut(arr.getJSONObject(i).getString("remote")) { mutableListOf() }.add(i)
        for ((name, idxs) in groups) {
            list.addView(TextView(this).apply { text = name; textSize = 18f; setPadding(0, 32, 0, 8) })
            val grid = GridLayout(this).apply { columnCount = 3 }
            for (i in idxs) {
                val o = arr.getJSONObject(i)
                val btn = Button(this).apply {
                    text = o.getString("label")
                    setOnClickListener { send(o.getString("code")) }
                    setOnLongClickListener { confirmDelete(i); true }
                }
                val lp = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f))
                lp.width = 0
                grid.addView(btn, lp)
            }
            list.addView(grid)
        }
    }

    private fun confirmDelete(i: Int) {
        AlertDialog.Builder(this).setMessage("Delete this button?")
            .setPositiveButton("Delete") { _, _ ->
                val a = load(); val n = JSONArray()
                for (k in 0 until a.length()) if (k != i) n.put(a.get(k))
                save(n); render()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showAdd() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }
        fun field(h: String) = EditText(this).apply { hint = h; inputType = InputType.TYPE_CLASS_TEXT; box.addView(this) }
        val r = field("Remote name (TV, AC, Fan)")
        val l = field("Button label (Power)")
        val c = field("NEC: addr cmd (hex) e.g. 00 12  |  or raw: 9000,4500,...")
        AlertDialog.Builder(this).setTitle("Add button").setView(box)
            .setPositiveButton("Save") { _, _ ->
                if (pattern(c.text.toString()) == null) { toast("Invalid code"); return@setPositiveButton }
                val a = load()
                a.put(JSONObject().put("remote", r.text.toString().ifBlank { "Remote" })
                    .put("label", l.text.toString().ifBlank { "Btn" }).put("code", c.text.toString()))
                save(a); render()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun send(code: String) {
        val p = pattern(code) ?: return toast("Invalid code")
        if (ir?.hasIrEmitter() == true) ir?.transmit(p.first, p.second) else toast("No IR blaster")
    }

    private fun pattern(code: String): Pair<Int, IntArray>? = try {
        val c = code.trim()
        if (c.contains(",")) Pair(38000, c.split(",").map { it.trim().toInt() }.toIntArray())
        else {
            val p = c.split(Regex("[\\s:/]+"))
            nec(p[0].removePrefix("0x").toInt(16), p[1].removePrefix("0x").toInt(16))
        }
    } catch (e: Exception) { null }

    private fun nec(a: Int, d: Int): Pair<Int, IntArray> {
        val l = ArrayList<Int>()
        l.add(9000); l.add(4500)
        for (byte in intArrayOf(a and 0xFF, a.inv() and 0xFF, d and 0xFF, d.inv() and 0xFF))
            for (i in 0..7) { l.add(560); l.add(if (((byte shr i) and 1) == 1) 1690 else 560) }
        l.add(560)
        return Pair(38000, l.toIntArray())
    }
}

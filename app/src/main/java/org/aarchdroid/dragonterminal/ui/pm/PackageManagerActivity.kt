package org.aarchdroid.dragonterminal.ui.pm

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.view.MenuItemCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import org.aarchdroid.dragonterminal.util.SortedListAdapter
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.frontend.floating.TerminalDialog
import org.aarchdroid.dragonterminal.ui.pm.adapter.PackageAdapter
import org.aarchdroid.dragonterminal.ui.pm.model.PackageModel
import org.aarchdroid.dragonterminal.ui.pm.model.PacmanPackage
import org.aarchdroid.dragonterminal.ui.pm.utils.StringDistance
import org.aarchdroid.dragonterminal.backend.ChrootManager
import org.aarchdroid.dragonterminal.utils.PackageUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.min

/**
 * @author kiva
 */

class PackageManagerActivity : AppCompatActivity(), SearchView.OnQueryTextListener, SortedListAdapter.Callback {
    companion object {
        private const val BATCH_SIZE = 300
    }

    private val COMPARATOR = SortedListAdapter.ComparatorBuilder<PackageModel>()
            .setOrderForModel<PackageModel>(PackageModel::class.java) { a, b ->
                a.pkg.name.compareTo(b.pkg.name)
            }
            .build()

    lateinit var recyclerView: RecyclerView
    lateinit var adapter: PackageAdapter
    lateinit var models: ArrayList<PackageModel>

    private val searchHandler = Handler(Looper.getMainLooper())
    private val batchHandler = Handler(Looper.getMainLooper())
    private val searchRunnable = Runnable { performSearch() }
    private var pendingQuery: String? = null
    private var currentInsertIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ui_pm_single_tab)
        val toolbar = findViewById<Toolbar>(R.id.pm_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById<RecyclerView>(R.id.pm_package_list)
        recyclerView.setHasFixedSize(true)
        adapter = PackageAdapter(this, COMPARATOR, object : PackageAdapter.Listener {
            override fun onModelClicked(model: PackageModel) {
                AlertDialog.Builder(this@PackageManagerActivity)
                        .setTitle("${model.pkg.repo}/${model.pkg.name}")
                        .setMessage(model.getPackageDetails())
                        .setPositiveButton(R.string.install, { _, _ ->
                            installPackage(model.pkg.name)
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show()
            }

        })
        adapter.addCallback(this)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        models = ArrayList()
        refreshPackageList()
    }

    private fun installPackage(packageName: String) {
        TerminalDialog(this@PackageManagerActivity)
                .execute("su", arrayOf("-M", "-c",
                        "env PACMAN_DISABLE_SANDBOX=1 chroot /data/local/aarchdroid /usr/bin/pacman -S --needed --noconfirm $packageName"))
                .onFinish(object : TerminalDialog.SessionFinishedCallback {
                    override fun onSessionFinished(dialog: TerminalDialog, finishedSession: TerminalSession?) {
                        dialog.setTitle(getString(R.string.done))
                    }
                })
                .imeEnabled(true)
                .show("Installing $packageName")
        Toast.makeText(this, R.string.installing_topic, Toast.LENGTH_LONG).show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_pm, menu)
        val searchItem = menu!!.findItem(R.id.action_search)
        val searchView = MenuItemCompat.getActionView(searchItem) as SearchView
        searchView.setOnQueryTextListener(this)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.action_update_and_refresh -> executePacmanUpdate()
            R.id.action_refresh -> refreshPackageList()
            R.id.action_upgrade -> executePacmanUpgrade()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun executePacmanUpdate() {
        PackageUtils.pacman(this, arrayOf("pacman", "-Sy"), { exitStatus, dialog ->
            if (exitStatus != 0) {
                dialog.setTitle(getString(R.string.error))
                return@pacman
            }
            Toast.makeText(this, R.string.pacman_update_ok, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            refreshPackageList()
        })
    }

    private fun executePacmanUpgrade() {
        PackageUtils.pacman(this, arrayOf("pacman", "-Su", "--noconfirm"), { exitStatus, dialog ->
            if (exitStatus != 0) {
                dialog.setTitle(getString(R.string.error))
                return@pacman
            }
            Toast.makeText(this, R.string.pacman_upgrade_ok, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        })
    }

    private fun refreshPackageList() {
        models.clear()
        currentInsertIndex = 0
        adapter.edit().replaceAll(emptyList<PackageModel>()).commit()
        Thread {
            try {
                if (!ChrootManager.ensureMounted()) {
                    this@PackageManagerActivity.runOnUiThread {
                        Toast.makeText(this@PackageManagerActivity, "Chroot not mounted", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }
                val process = Runtime.getRuntime().exec(arrayOf(
                    "su", "-M", "-c",
                    "env PACMAN_DISABLE_SANDBOX=1 chroot /data/local/aarchdroid /usr/bin/pacman -Sl"
                ))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val parts = line!!.split(" ".toRegex(), 3)
                    if (parts.size >= 3) {
                        val pkg = PacmanPackage(name = parts[1], version = parts[2], repo = parts[0])
                        models.add(PackageModel(pkg))
                    }
                }
                reader.close()
                process.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            this@PackageManagerActivity.runOnUiThread {
                insertBatch(0)
                if (models.isEmpty()) {
                    Toast.makeText(this@PackageManagerActivity, R.string.package_list_empty, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun insertBatch(start: Int) {
        if (pendingQuery != null && pendingQuery != "") return
        currentInsertIndex = start
        val end = min(start + BATCH_SIZE, models.size)
        val batch = adapter.edit()
        batch.add(models.subList(start, end))
        batch.commit()
        currentInsertIndex = end
        if (end < models.size) {
            batchHandler.postDelayed({ insertBatch(end) }, 1)
        }
    }

    private fun sortDistance(models: List<PackageModel>, query: String,
                             mapper: (PacmanPackage) -> String): List<Pair<PackageModel, Int>> {
        return models
                .map({
                    Pair(it, StringDistance.distance(mapper(it.pkg).lowercase(), query.lowercase()))
                })
                .sortedWith(Comparator { l, r -> r.second.compareTo(l.second) })
                .toList()
    }

    private fun filter(models: List<PackageModel>, query: String): List<PackageModel> {
        val filteredModelList = mutableListOf<PackageModel>()
        val prepared = models.filter {
            it.pkg.name.contains(query, true)
        }

        sortDistance(prepared, query, { it.name }).mapTo(filteredModelList, { it.first })
        return filteredModelList
    }

    override fun onQueryTextSubmit(text: String?): Boolean {
        searchHandler.removeCallbacks(searchRunnable)
        pendingQuery = text
        performSearch(true)
        return true
    }

    override fun onQueryTextChange(text: String?): Boolean {
        pendingQuery = text
        searchHandler.removeCallbacks(searchRunnable)
        searchHandler.postDelayed(searchRunnable, 300)
        return true
    }

    private fun performSearch(forceSync: Boolean = false) {
        val query = pendingQuery ?: return
        if (query.isEmpty()) {
            batchHandler.removeCallbacksAndMessages(null)
            adapter.edit().replaceAll(models).commit()
            currentInsertIndex = models.size
            return
        }
        batchHandler.removeCallbacksAndMessages(null)
        val runnable = Runnable {
            val filteredModelList = filter(models, query)
            runOnUiThread {
                adapter.edit()
                        .replaceAll(filteredModelList)
                        .commit()
            }
        }
        if (forceSync) {
            runnable.run()
        } else {
            Thread(runnable).start()
        }
    }

    override fun onEditStarted() {
        recyclerView.animate().alpha(0.5f)
    }

    override fun onEditFinished() {
        recyclerView.scrollToPosition(0)
        recyclerView.animate().alpha(1.0f)
    }
}
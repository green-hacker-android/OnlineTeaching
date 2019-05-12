package com.codingblocks.cbonlineapp.activities

import android.animation.LayoutTransition
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.codingblocks.cbonlineapp.BuildConfig
import com.codingblocks.cbonlineapp.R
import com.codingblocks.cbonlineapp.Utils.retrofitCallback
import com.codingblocks.cbonlineapp.adapters.TabLayoutAdapter
import com.codingblocks.cbonlineapp.database.AppDatabase
import com.codingblocks.cbonlineapp.database.models.CourseRun
import com.codingblocks.cbonlineapp.database.models.DoubtsModel
import com.codingblocks.cbonlineapp.database.models.NotesModel
import com.codingblocks.cbonlineapp.fragments.VideoDoubtFragment
import com.codingblocks.cbonlineapp.fragments.VideoNotesFragment
import com.codingblocks.cbonlineapp.util.MediaUtils
import com.codingblocks.cbonlineapp.util.MyVideoControls
import com.codingblocks.cbonlineapp.util.OnItemClickListener
import com.codingblocks.cbonlineapp.extensions.pageChangeCallback
import com.codingblocks.onlineapi.Clients
import com.codingblocks.onlineapi.models.Contents
import com.codingblocks.onlineapi.models.DoubtsJsonApi
import com.codingblocks.onlineapi.models.Notes
import com.codingblocks.onlineapi.models.RunAttemptsModel
import com.devbrackets.android.exomedia.listener.OnPreparedListener
import com.google.android.youtube.player.YouTubeInitializationResult
import com.google.android.youtube.player.YouTubePlayer
import com.google.android.youtube.player.YouTubePlayerSupportFragment
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import kotlinx.android.synthetic.main.activity_video_player.*
import kotlinx.android.synthetic.main.doubt_dialog.view.*
import kotlinx.android.synthetic.main.exomedia_default_controls_mobile.view.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import kotlin.concurrent.thread


class VideoPlayerActivity : AppCompatActivity(),
        OnPreparedListener,
        OnItemClickListener, AnkoLogger {

    private var youtubePlayer: YouTubePlayer? = null
    private var pos: Long? = 0
    private lateinit var youtubePlayerInit: YouTubePlayer.OnInitializedListener
    private lateinit var attemptId: String
    private lateinit var contentId: String

    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    private val doubtsDao by lazy {
        database.doubtsDao()
    }

    private val notesDao by lazy {
        database.notesDao()
    }

    private val courseDao by lazy {
        database.courseDao()
    }

    private val runDao by lazy {
        database.courseRunDao()
    }

    override fun onItemClick(position: Int, id: String) {
        if (contentId == id) {
            if (displayYoutubeVideo.view?.visibility == View.VISIBLE)
                youtubePlayer?.seekToMillis(position * 1000)
            else
                videoView.seekTo(position.toLong() * 1000)
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.codingblocks.cbonlineapp.R.layout.activity_video_player)
        rootLayout.layoutTransition
                .enableTransitionType(LayoutTransition.CHANGING)
        val controls = MyVideoControls(this)
        videoView.setControls(controls)
        (videoView.videoControls as MyVideoControls).let {
            it.fullscreenBtn.setOnClickListener {
                val i = Intent(this, VideoPlayerFullScreenActivity::class.java)
                i.putExtra("FOLDER_NAME", videoView.videoUri.toString())
                i.putExtra("CURRENT_POSITION", videoView.currentPosition)
                startActivityForResult(i, 1)
            }

        }

        val url = intent.getStringExtra("FOLDER_NAME")
        val youtubeUrl = intent.getStringExtra("videoUrl")
        attemptId = intent.getStringExtra("attemptId")
        contentId = intent.getStringExtra("contentId")
        val downloaded = intent.getBooleanExtra("downloaded", false)



        if (youtubeUrl != null) {
            displayYoutubeVideo.view?.visibility = View.VISIBLE
            setupYoutubePlayer(youtubeUrl)
        } else {
            displayYoutubeVideo.view?.visibility = View.GONE
            videoView.visibility = View.VISIBLE
            setupVideoView(url, downloaded)
        }
        setupViewPager(attemptId)
    }

    private fun setupViewPager(attemptId: String) {
        val adapter = TabLayoutAdapter(supportFragmentManager)
        adapter.add(VideoDoubtFragment.newInstance(attemptId), "Doubts")
        adapter.add(VideoNotesFragment.newInstance(attemptId), "Notes")

        player_viewpager.adapter = adapter
        player_tabs.setupWithViewPager(player_viewpager)
        player_viewpager.offscreenPageLimit = 2
        player_viewpager.addOnPageChangeListener(
            pageChangeCallback(
                fnSelected = { position ->
                    when (position) {
                        0 -> {
                            videoFab.setOnClickListener {
                                createDoubt()
                            }
                        }
                        1 -> {
                            videoFab.setOnClickListener {
                                val notePos: Double =
                                    if (displayYoutubeVideo.view?.visibility == View.VISIBLE)
                                        (youtubePlayer?.currentTimeMillis!! / 1000).toDouble()
                                    else
                                        (videoView.currentPosition.toInt() / 1000).toDouble()
                                createNote(notePos)
                            }
                        }
                    }
                },
                fnState = {},
                fnScrolled = { _: Int, _: Float, _: Int ->
                })
        )
    }

    private fun createNote(notePos: Double) {
        val noteDialog = AlertDialog.Builder(this).create()
        val noteView = layoutInflater.inflate(R.layout.doubt_dialog, null)
        noteView.descriptionLayout.visibility = View.GONE
        noteView.title.text = "Create A Note"
        noteView.okBtn.text = "Create Note"


        noteView.cancelBtn.setOnClickListener {
            noteDialog.dismiss()
        }
        noteView.okBtn.setOnClickListener {
            if (noteView.titleLayout.editText!!.text.isEmpty()) {
                noteView.titleLayout.error = "Note Cannot Be Empty."
                return@setOnClickListener
            } else {
                noteView.descriptionLayout.error = ""
                val note = Notes()
                note.text = noteView.titleLayout.editText!!.text.toString()
                note.duration = notePos
                val runAttempts = RunAttemptsModel() // type run_attempts
                val contents = Contents() // type contents
                runAttempts.id = attemptId
                contents.id = contentId
                note.runAttempt = runAttempts
                note.content = contents
                Clients.onlineV2JsonApi.createNote(note).enqueue(retrofitCallback { throwable, response ->
                    response?.body().let {
                        noteDialog.dismiss()
                        if (response?.isSuccessful!!)
                            try {
                                notesDao.insert(
                                    NotesModel(
                                        it!!.id
                                            ?: "", it.duration ?: 0.0, it.text ?: "", it.content?.id
                                            ?: "", attemptId, it.createdAt
                                            ?: "", it.deletedAt
                                            ?: ""
                                    )
                                )
                            } catch (e: Exception) {
                                info { "error" + e.localizedMessage }
                            }
                    }
                })
            }
        }

        noteDialog.window.setBackgroundDrawableResource(android.R.color.transparent)
        noteDialog.setView(noteView)
        noteDialog.setCancelable(false)
        noteDialog.show()
    }

    private fun setupYoutubePlayer(youtubeUrl: String) {
        youtubePlayerInit = object : YouTubePlayer.OnInitializedListener {
            override fun onInitializationFailure(p0: YouTubePlayer.Provider?, p1: YouTubeInitializationResult?) {
            }

            override fun onInitializationSuccess(p0: YouTubePlayer.Provider?, youtubePlayerInstance: YouTubePlayer?, p2: Boolean) {
                if (!p2) {
                    youtubePlayer = youtubePlayerInstance
                    youtubePlayerInstance?.loadVideo(youtubeUrl.substring(32))
                }
            }
        }
        val youTubePlayerSupportFragment = supportFragmentManager.findFragmentById(R.id.displayYoutubeVideo) as YouTubePlayerSupportFragment?
        youTubePlayerSupportFragment!!.initialize(BuildConfig.YOUTUBE_KEY, youtubePlayerInit)


    }

    private fun setupVideoView(url: String, downloaded: Boolean) {
        videoView.setOnPreparedListener(this)
        if (downloaded) {
            videoView.setVideoURI(MediaUtils.getCourseVideoUri(url, this))
        } else {
            videoView.setVideoURI(Uri.parse(url))
        }
        videoView.setOnCompletionListener {
            finish()
        }
    }

    override fun onPrepared() {
        videoView.start()
    }

    override fun onPause() {
        super.onPause()
        videoView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        videoView.release()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == -1) {
            pos = data?.getLongExtra("CURRENT_POSITION", 0)
            videoView.seekTo(pos ?: 0)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase))
    }

    private fun createDoubt() {

        runDao.getRunByAtemptId(attemptId).observe(this, Observer<CourseRun> {
            val categoryId = courseDao.getCourse(it?.crCourseId!!).categoryId
            val doubtDialog = AlertDialog.Builder(this).create()
            val doubtView = layoutInflater.inflate(R.layout.doubt_dialog, null)
            doubtView.cancelBtn.setOnClickListener {
                doubtDialog.dismiss()
            }
            doubtView.okBtn.setOnClickListener {
                if (doubtView.titleLayout.editText!!.text.length < 15 || doubtView.titleLayout.editText!!.text.isEmpty()) {
                    doubtView.titleLayout.error = "Title length must be atleast 15 characters."
                    return@setOnClickListener
                } else if (doubtView.descriptionLayout.editText!!.text.length < 20 || doubtView.descriptionLayout.editText!!.text.isEmpty()) {
                    doubtView.descriptionLayout.error = "Description length must be atleast 20 characters."
                    doubtView.titleLayout.error = ""
                } else {
                    doubtView.descriptionLayout.error = ""
                    val doubt = DoubtsJsonApi()
                    doubt.body = doubtView.descriptionLayout.editText!!.text.toString()
                    doubt.title = doubtView.titleLayout.editText!!.text.toString()
                    doubt.category = categoryId
                    val runAttempts = RunAttemptsModel() // type run-attempts
                    val contents = Contents() // type contents
                    runAttempts.id = attemptId
                    contents.id = contentId
                    doubt.status = "PENDING"
                    doubt.postrunAttempt = runAttempts
                    doubt.content = contents
                    Clients.onlineV2JsonApi.createDoubt(doubt).enqueue(retrofitCallback { throwable, response ->
                        response?.body().let {
                            doubtDialog.dismiss()
                            thread {
                                doubtsDao.insert(
                                    DoubtsModel(
                                        it!!.id
                                            ?: "", it.title, it.body, it.content?.id
                                            ?: "", it.status, it.runAttempt?.id ?: ""
                                    )
                                )
                            }
                        }
                    })
                }
            }

            doubtDialog.window.setBackgroundDrawableResource(android.R.color.transparent)
            doubtDialog.setView(doubtView)
            doubtDialog.setCancelable(false)
            doubtDialog.show()
        })
    }


}

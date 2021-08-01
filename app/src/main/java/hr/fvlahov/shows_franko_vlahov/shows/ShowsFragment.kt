package hr.fvlahov.shows_franko_vlahov.shows

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import hr.fvlahov.shows_franko_vlahov.BuildConfig
import hr.fvlahov.shows_franko_vlahov.R
import hr.fvlahov.shows_franko_vlahov.ShowsApp
import hr.fvlahov.shows_franko_vlahov.databinding.DialogProfileBinding
import hr.fvlahov.shows_franko_vlahov.databinding.FragmentShowsBinding
import hr.fvlahov.shows_franko_vlahov.login.REMEMBER_ME_LOGIN
import hr.fvlahov.shows_franko_vlahov.login.USER_EMAIL
import hr.fvlahov.shows_franko_vlahov.login.USER_IMAGE
import hr.fvlahov.shows_franko_vlahov.model.api_response.Show
import hr.fvlahov.shows_franko_vlahov.utils.FileUtil
import hr.fvlahov.shows_franko_vlahov.utils.preparePermissionsContract
import hr.fvlahov.shows_franko_vlahov.viewmodel.ShowViewModel
import hr.fvlahov.shows_franko_vlahov.viewmodel.ShowViewModelFactory
import java.lang.Exception

class ShowsFragment : Fragment() {

    private val viewModel: ShowViewModel by viewModels {
        ShowViewModelFactory((activity?.application as ShowsApp).showsDatabase)
    }

    private var showsVisibility = false

    private var _binding: FragmentShowsBinding? = null
    private val binding get() = _binding!!

    private var adapter: ShowsAdapter? = null

    private val cameraPermission = preparePermissionsContract({ takePhoto() })

    private val takeImageResult =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) {
                onTakePictureSuccess()
            }
        }

    private var latestTmpUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentShowsBinding.inflate(layoutInflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initShowsRecyclerView()
        initShowHideEmptyStateButton()

        binding.buttonShowProfile.setOnClickListener { onShowProfileClicked() }
        viewModel.getShows()

        initViewModelLiveData()
    }

    private fun initViewModelLiveData() {
        viewModel.getShowsLiveData().observe(
            viewLifecycleOwner,
            { shows ->
                updateShows(shows)
            })

        viewModel.getProfileDetails(requireActivity().getPreferences(Activity.MODE_PRIVATE))

        viewModel.getProfileLiveData().observe(
            requireActivity(),
            { user ->
                setProfileImageIfExists(binding.buttonShowProfile, user.imageUrl)
                saveImageUrlToPrefs(user.imageUrl)
            })
    }

    private fun saveImageUrlToPrefs(imageUrl: String?) {
        activity?.getPreferences(Activity.MODE_PRIVATE)?.edit()?.apply {
            putString(
                USER_IMAGE,
                imageUrl
            )
            apply()
        }
    }

    private fun setProfileImageIfExists(imageView: ImageView, imageUrl: String?) {
        try {
            Glide.with(requireContext()).load(imageUrl)
                .into(imageView)
        } catch (e: Exception) {
            Log.d("ShowsFragment", e.message ?: "")
            imageView.setImageResource(R.drawable.ic_profile_placeholder)
        }
    }

    private fun onTakePictureSuccess() {
        viewModel.uploadAvatarImage(FileUtil.getImageFile(context)?.absolutePath ?: "")
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun takePhoto() {
        lifecycleScope.launchWhenStarted {
            getTmpFileUri().let { uri ->
                latestTmpUri = uri
                takeImageResult.launch(uri)
            }
        }
    }

    private fun getTmpFileUri(): Uri {
        val tmpFile = FileUtil.createImageFile(requireContext()) ?: return Uri.parse("")

        return FileProvider.getUriForFile(
            requireContext(),
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            tmpFile
        )
    }

    private fun onShowProfileClicked() {
        val bottomSheetDialog = BottomSheetDialog(this.requireContext())

        val bottomSheetBinding = DialogProfileBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        val prefs = activity?.getPreferences(Context.MODE_PRIVATE)
        val userEmail = prefs?.getString(USER_EMAIL, "associate")

        bottomSheetBinding.labelUsername.text = userEmail

        viewModel.getProfileLiveData().value?.let {
            setProfileImageIfExists(
                bottomSheetBinding.imageProfile,
                it.imageUrl
            )
        }

        bottomSheetBinding.buttonLogout.setOnClickListener {
            onButtonLogoutClicked(bottomSheetDialog)
        }

        bottomSheetBinding.buttonChangeProfilePhoto.setOnClickListener {
            onButtonChangeProfilePhotoClicked()
        }

        bottomSheetDialog.show()
    }


    private fun onButtonChangeProfilePhotoClicked() {
        cameraPermission.launch(arrayOf(android.Manifest.permission.CAMERA))
    }

    private fun onButtonLogoutClicked(bottomSheetDialog: BottomSheetDialog) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext()).apply {
            setCancelable(true)
            setTitle(getString(R.string.are_you_sure))
            setMessage(getString(R.string.are_you_sure_logout))
            setPositiveButton(getString(R.string.confirm)) { alertDialog, which ->
                onConfirmLogoutClicked(
                    bottomSheetDialog
                )
            }
            setNegativeButton(getString(android.R.string.cancel)) { dialog, which -> dialog.dismiss() }
        }

        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()
    }

    private fun onConfirmLogoutClicked(bottomSheetDialog: BottomSheetDialog) {
        bottomSheetDialog.dismiss()
        logout()
    }

    private fun initShowHideEmptyStateButton() {
        binding.buttonShowHideEmptyState.setOnClickListener {
            if (showsVisibility) {
                binding.imageEmptyShows.visibility = View.GONE
                binding.labelEmptyShows.visibility = View.GONE
                binding.recyclerShows.visibility = View.VISIBLE
            } else {
                binding.imageEmptyShows.visibility = View.VISIBLE
                binding.labelEmptyShows.visibility = View.VISIBLE
                binding.recyclerShows.visibility = View.GONE
            }
            showsVisibility = !showsVisibility
        }
    }

    private fun updateShows(shows: List<Show>) {
        adapter?.setItems(shows)

        if (adapter?.itemCount ?: 0 < 1) {
            binding.recyclerShows.visibility = View.GONE
        } else {
            binding.imageEmptyShows.visibility = View.GONE
            binding.labelEmptyShows.visibility = View.GONE
        }
    }

    private fun initShowsRecyclerView() {
        binding.recyclerShows.layoutManager =
            LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)

        adapter = ShowsAdapter(listOf()) { show ->
            onShowClicked(show)
        }
        binding.recyclerShows.adapter = adapter
    }

    private fun onShowClicked(show: Show) {
        val action = ShowsFragmentDirections.actionShowsToShowDetails(show.id)
        findNavController().navigate(action)
    }

    private fun logout() {
        val prefs = activity?.getPreferences(Context.MODE_PRIVATE)
        with(prefs?.edit()) {
            this?.putBoolean(REMEMBER_ME_LOGIN, false)
            this?.apply()
        }
        findNavController().navigate(R.id.action_shows_to_login)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.mychuannong.app.im.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.mychuannong.app.R
import com.mychuannong.app.common.api.DrawerController
import com.mychuannong.app.databinding.FragmentIMBinding
import com.mychuannong.app.im.data.ChatListItem

class IMFragment : Fragment() {
    private lateinit var binding: FragmentIMBinding
    private val TAG = "IMFragment"

    private var drawerController: DrawerController? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is DrawerController) {
            drawerController = context
        } else {
            Log.d(TAG, "DrawerControl,宿主Activity未实现接口")
        }
    }

    override fun onDetach() {
        drawerController = null
        super.onDetach()
    }

//    override fun onResume() {
//        super.onResume()
//        imName.text = UserInfoManager.account
//        GlideUtils.loadWithThumbnail(
//            this,
//            imAvatar,
//            UserInfoManager.userAvatar
//        )
//    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentIMBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.ivUnderConstruction.setImageResource(R.drawable.ic_feature_preparing)
//        imAvatar.setOnClickListener { drawerController?.openDrawer() }
//        recyclerView.adapter = IMChatListAdapter(chatList)
//        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private val chatList = listOf(
        ChatListItem(
            groupAvatar = "",
            groupName = "Brooke Davis",
            lastMessage = "I am who I am. No excuses.",
            messageCount = 2
        ),
        ChatListItem(
            groupAvatar = "",
            groupName = "Brooke Davis",
            lastMessage = "I am who I am. No excuses.",
            messageCount = 2
        )
    )

    companion object {
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance() =
            IMFragment().apply {}
    }
}

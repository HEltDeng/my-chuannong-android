package com.mychuannong.app.freshNews.data.remote.repository

import android.util.Log
import com.mychuannong.app.core.network.ApiResponse
import com.mychuannong.app.freshNews.data.local.mmkv.model.FreshNewsItem
import com.mychuannong.app.freshNews.data.local.mmkv.model.LevelCommentRequest
import com.mychuannong.app.freshNews.data.remote.api.CommentsApi
import com.mychuannong.app.utils.RetrofitUtils
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

class CommentsRepository private constructor() {
    companion object{
        val instance by lazy { CommentsRepository() }
    }
    private val TAG = "CommentsRepository"
    private val service = lazy { RetrofitUtils.instanceComments.create(CommentsApi::class.java) }
    fun loadLevel1Comments(freshNewsItem: FreshNewsItem,page:Int,pageSize:Int,userId: Int) = flow{
//            Log.d(TAG, "loadLevel1Comments: loading level 1 comments for freshNewsId ${freshNewsItem.freshNewsId}, page $page, pageSize $pageSize")
            val response = service.value.getLevel1Comments(freshNewsItem.freshNewsId,page,pageSize,userId)
            if (response.code == "200"){
                Log.d(TAG, "loadLevel1Comments: success")
                emit(ApiResponse.Success(response.data))
            }
            else{
                Log.d(TAG, "loadLevel1Comments: failed with code ${response.code}")
                emit(ApiResponse.Error(response.msg))
            }
        }.catch {e->
            Log.e(TAG, "loadLevel1Comments: exception ${e.message}",e)
            emit(ApiResponse.Error(e.message ?: "Unknown error"))
    }
    fun sendComment(freshNewsId:Int,userId:Int,content: String,parentId:Int) = flow{
        // 地区/IP 由自有后端按需从请求上下文推导。
        val ip = "未知"
        //发布一级评论
        if (parentId == 0) {
            service.value.addLevel1Comment(LevelCommentRequest(
                freshNewsId = freshNewsId,
                userId = userId,
                content = content,
                parentCommentId = 0,
                commentIp = ip
            )).let {response->
                if (response.code == "200"){
                    Log.d(TAG, "sendComment: success")
                    emit(ApiResponse.Success(response.data))
                }
                else{
                    Log.d(TAG, "sendComment: failed with code ${response.code}")
                    emit(ApiResponse.Error(response.msg))
                }
            }
        }
        else{
            //发布二级评论
            service.value.addLevel2Comment(LevelCommentRequest(
                freshNewsId = freshNewsId,
                userId = userId,
                content = content,
                parentCommentId = parentId,
                commentIp = ip
            )).let {response->
                if (response.code == "200"){
                    Log.d(TAG, "sendComment: success")
                    emit(ApiResponse.Success(response.data))
                }
                else{
                    Log.d(TAG, "sendComment: failed with code ${response.code}")
                    emit(ApiResponse.Error(response.msg))
                }
            }
        }
    }.catch {e->
        Log.e(TAG, "sendComment: exception ${e.message}",e)
        emit(ApiResponse.Error(e.message ?: "Unknown error"))
    }
    fun likeComment(commentId:Int,userId: Int,isParent:Int) = flow{
        val response = service.value.likeComment(commentId,userId,isParent)
        if (response.code == "200"){
            Log.d(TAG, "likeComment: success")
            emit(ApiResponse.Success(true))
        }
        else{
            Log.d(TAG, "likeComment: failed with code ${response.code}")
            emit(ApiResponse.Error(response.msg))
        }
    }.catch {e->
        Log.e(TAG, "likeComment: exception ${e.message}",e)
        emit(ApiResponse.Error(e.message ?: "Unknown error"))
    }
    fun refreshLikeCount() = flow{
        val response = service.value.refreshLikeCount()
        if (response.code == "200"){
            Log.d(TAG, "refreshLikeCount: success")
            emit(ApiResponse.Success(true))
        }
        else{
            Log.d(TAG, "refreshLikeCount: failed with code ${response.code}")
            emit(ApiResponse.Error(response.msg))
        }
    }.catch {e->
        Log.e(TAG, "refreshLikeCount: exception ${e.message}",e)
        emit(ApiResponse.Error(e.message ?: "Unknown error"))
    }
    fun loadLevel2Comments(
        freshNewsId: Int,
        parentCommentId: Int,
        page: Int,
        pageSize: Int,
        userId: Int
    ) = flow {
        val response =
            service.value.getLevel2Comments(freshNewsId, page, pageSize, parentCommentId, userId)
        if (response.code == "200") {
            Log.d(TAG, "loadLevel2Comments: success")
            emit(ApiResponse.Success(response.data))
        } else {
            Log.d(TAG, "loadLevel2Comments: failed with code ${response.code}")
            emit(ApiResponse.Error(response.msg))
        }
    }.catch { e ->
        Log.e(TAG, "loadLevel2Comments: exception ${e.message}", e)
        emit(ApiResponse.Error(e.message ?: "Unknown error"))
    }

}

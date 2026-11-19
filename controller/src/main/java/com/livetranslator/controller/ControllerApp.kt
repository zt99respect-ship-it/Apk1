package com.livetranslator.controller

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.livetranslator.controller.data.ControllerRepository

class ControllerApp : Application() {

  val auth: FirebaseAuth by lazy { Firebase.auth }

  val firestore: FirebaseFirestore by lazy {
    FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
  }

  val controllerRepository: ControllerRepository by lazy {
    ControllerRepository(firestore = firestore, auth = auth)
  }
}

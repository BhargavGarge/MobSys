TrailHead

TrailHead is a mobile application designed for outdoor enthusiasts who enjoy hiking, trail running, and cycling. It is an all-in-one tool for discovering, navigating, and sharing outdoor adventures.

📱 Features

User Authentication – Secure sign-up and login via Firebase Authentication.

Route Discovery & Navigation – Explore nearby trails based on location, filter by activity type (Hiking, Running, Cycling), and get turn-by-turn GPS navigation.

Activity Tracking – Counts steps using the phone’s pedometer sensor while navigating.

User Profile & Progress – Tracks daily step goals, active days, and total distance. Manage saved and liked routes.

Social Feed – Share photos and experiences, interact with likes and comments.

Find Adventure Buddies – Uses Bluetooth to scan for and connect with nearby users for spontaneous adventures.

Help & Support – In-app FAQ and support contact options.

🛠️ Tech Stack

Frontend: Android (Java)

Backend: Google Firebase (Authentication, Cloud Firestore, Storage)

APIs & Sensors:

GPS (Location Services for navigation and maps)

Pedometer Sensor (step tracking)

Bluetooth API (Find Adventure Buddies feature)

UI/UX: Custom Views (circular step progress bar), clean and intuitive layout

📸 Screens & Flow
Onboarding & Authentication

Welcome screen with app highlights

Sign-up / login with Firebase Authentication

Main Dashboard

Personalized welcome

Weather widget

Quick access to plan routes and record activities

Route Discovery & Navigation

Interactive map with filters

Trail details: difficulty, distance, rating

Turn-by-turn navigation with live step counter

Social Features

Feed: Posts with photos, likes, and comments

Find Adventure Buddies: Bluetooth scanning + pop-up explanation

Chat: Direct communication between nearby users

User Profile & Progress

Profile with personal info, active days, daily steps progress bar

My Routes: saved routes, liked routes, total distance covered

🚀 Project Requirements Fulfilled

✔️ More than 3 Activities

✔️ Data transfer between Activities

✔️ Integration of 2+ sensors (GPS + Pedometer)

✔️ Advanced feature with Bluetooth API

✔️ Custom View implemented (circular step progress bar)


📂 Installation

Clone this repository:

git clone https://github.com/your-username/trailhead.git
cd trailhead


Open the project in Android Studio.

Connect Firebase (Authentication, Firestore, Storage).

Enable permissions in AndroidManifest.xml for:

Location

Activity Recognition

Bluetooth

Run on device/emulator.

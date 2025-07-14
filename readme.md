# Nordic Data Pi MX Sensor Alert 🔬

A comprehensive Mendix web application for real-time IoT sensor monitoring and patient physiotherapy management, featuring Nordic Thingy and Mbient Lab sensor integration with WoT (Web of Things) technology.

## 📋 Project Description

This Mendix application provides a professional healthcare platform for:
- **Real-time monitoring** of Nordic Thingy and Mbient Lab sensors via WoT protocol
- **Patient management** with comprehensive profiles and exercise tracking
- **Physiotherapy exercise guidance** with motion detection and analysis
- **Interactive dashboards** with live sensor data visualization
- **Secure authentication** with role-based access control

## 🚀 Key Features

### 📊 Dashboard & Analytics
- Real-time sensor data overview with live charts
- Patient progress tracking and metrics
- Sensor health monitoring and alerts
- Performance analytics and reporting

### 🌡️ IoT Sensor Integration
- **Nordic Thingy**: Temperature, humidity, pressure, air quality monitoring
- **Mbient Lab**: 3D acceleration and motion detection
- **WoT Protocol**: Standardized IoT communication
- **Live Updates**: Automatic data refresh every 3 seconds

### 🏃 Motion Tracking & Exercise Management
- Real-time 3D motion visualization
- Guided physiotherapy exercises (Head Rotation, Neck Extension)
- Motion pattern analysis and feedback
- Exercise session recording and evaluation

### 👥 Patient Management System
- Complete patient profiles
- Exercise assignment
- Session history with CSV files
- Therapist-patient relationship management

### 🎯 Therapeutic Exercise Library
- Pre-defined exercise protocols
- Real-time motion guidance
- Automatic performance scoring
- Custom exercise creation tools

## 🏗️ Project Architecture

```
Mendix_Bachelor/
├── 📁 javascriptsource/           # JavaScript actions and modules
│   ├── nanoflowcommons/          # Common nanoflow utilities
│   │   └── actions/              # Geolocation, storage, navigation
│   ├── webactions/               # Web-specific actions
│   │   └── actions/              # Cookie handling, scrolling
│   └── wot_client/               # Web of Things client
│       └── actions/              # WoT property read/write/subscribe
├── 📁 theme/                     # Styling and themes
│   ├── web/                      # Web application styles
│   │   ├── SensorData.scss       # Sensor dashboard styling
│   │   ├── Patients.scss         # Patient management UI
│   │   ├── Motion.scss           # Motion tracking interface
│   │   ├── Chart.scss            # Analytics dashboard
│   │   └── *.scss               # Exercise-specific styles
│   └── native/                   # Native mobile styling
├── 📁 themesource/               # Atlas UI framework
│   └── atlas_core/              # Core UI components
├── 📁 javasource/                # Java backend logic
├── 📁 resources/                 # Static resources
├── 📁 userlib/                   # External libraries
└── 📁 widgets/                   # Custom widgets
```

## 🔧 Core Components and Modules

### 🌐 WoT (Web of Things) Integration

#### `javascriptsource/wot_client/actions/WoTReadProperty.js`
- **Role**: Real-time sensor data retrieval
- **Protocol**: HTTP-based WoT communication
- **Endpoint**: `http://153.109.22.167:8088/multi-sensor/properties/{propertyName}`
- **Features**: Nordic and Mbient sensor data fetching

#### `javascriptsource/wot_client/actions/WoTWriteProperty.js` & `javascriptsource/wot_client/actions/WoTSubscribeEvent.js`
- **Role**: Bidirectional IoT communication
- **Features**: Device control and event subscription

### 📱 NanoflowCommons Actions

#### Geolocation Services
- `GetCurrentLocation`: GPS positioning
- `GetCurrentLocationMinimumAccuracy`: Precision-based location
- `Geocode` & `ReverseGeocode`: Address conversion

#### Data Management
- `Base64Decode`: Data encoding/decoding
- `RefreshObject`: Entity synchronization
- `GetStorageItemObjectList`: Local storage management

#### Platform Detection
- `GetPlatform`: Web/Native/Hybrid detection
- `IsConnectedToServer`: Connectivity monitoring

### 🎨 Theme System

#### Web Styling Architecture
- **Base**: `custom-variables.scss` - Global design tokens
- **Components**: Modular SCSS files for each page/feature
- **Responsive**: Mobile-first design with adaptive layouts

#### Key Style Modules
```scss
// Sensor dashboard with real-time data visualization
@import "SensorData";

// Patient management interface
@import "Patients"; 

// Motion tracking and exercise guidance
@import "Motion";

// Analytics and reporting
@import "Chart";
```

### 🔒 Authentication & Security

#### User Management
- `SignIn` & `SignOut`: Authentication flow
- Role-based access control
- Session management with `ClearCachedSessionData`

## 📊 Data Structures

### Nordic Thingy Sensor Data
```javascript
{
  temperature: 22.5,      // °C
  humidity: 45.2,         // %RH
  pressure: 1013.25,      // hPa
  airQuality: 95,         // Air quality index
  timestamp: "2025-01-14T15:30:00Z"
}
```

### Mbient Lab Motion Data
```javascript
{
  acceleration: {
    x: 0.12,              // m/s² (lateral)
    y: -0.08,             // m/s² (anterior-posterior)
    z: 9.81               // m/s² (vertical)
  },
  timestamp: "2025-01-14T15:30:00Z"
}
```
## 🚀 Installation and Setup

### Prerequisites
- **Mendix Studio Pro** (v9.14 or higher)
- **Java Development Kit** (JDK 11+)
- **Node.js** (v16+) for custom widgets
- **PostgreSQL** or **SQL Server** for production database

### Step-by-Step Installation Guide

#### Step 1: Clone the Repository
```bash
git clone https://github.com/your-repo/mendix-bachelor.git
cd Mendix_Bachelor
```

#### Step 2: Open in Mendix Studio Pro
1. Launch **Mendix Studio Pro**
2. Click **Open App** → **Open App Locally**
3. Navigate to the cloned directory
4. Select `NordicDataPiMxSensorAlertUpdated_TEST.mpr`

#### Step 3: Configure Database
1. Go to **App Settings** → **Configurations**
2. Configure your database connection:
   - **Development**: Built-in database (automatic)
   - **Production**: PostgreSQL/SQL Server

#### Step 4: Install Dependencies
```bash
# Install JavaScript dependencies
cd javascriptsource
npm install

# Install theme dependencies
cd ../theme
npm install
```

#### Step 5: Configure WoT Endpoints
Update the sensor endpoints in `WoTReadProperty.js`:
```javascript
const url = `http://YOUR_SENSOR_IP:8088/multi-sensor/properties/${propertyName}`;
```

#### Step 6: Run the Application
1. Click **Run Locally** (F5) in Mendix Studio Pro
2. Wait for the application to start
3. Open browser to `http://localhost:8080`

### Development Environment Setup

#### Custom Widget Development
```bash
# Navigate to widgets directory
cd widgets/

# Create new widget
mendix create-widget MyCustomWidget

# Build widgets
npm run build
```

#### Theme Customization
```bash
# Navigate to theme directory
cd theme/web/

# Compile SCSS
npm run build-css

# Watch for changes
npm run watch-css
```


### Troubleshooting

#### Common Issues

1. **WoT Connection Failed**
   ```javascript
   // Check sensor endpoint accessibility
   curl http://153.109.22.167:8088/multi-sensor/properties/temperature
   ```

2. **Database Connection Issues**
   - Verify database credentials in **App Settings**
   - Check network connectivity
   - Ensure database server is running

3. **Theme Compilation Errors**
   ```bash
   # Clear theme cache
   cd theme/
   rm -rf node_modules package-lock.json
   npm install
   ```

4. **JavaScript Action Errors**
   - Check browser console for JavaScript errors
   - Verify all required libraries are included
   - Test actions in **Mendix Console**

## 🛠️ Technology Stack

### Core Platform
- **Mendix Platform** - Low-code application development
- **Atlas UI Framework** - Responsive design system
- **React Native** - Mobile application framework

### IoT & Communication
- **WoT (Web of Things)** - Standardized IoT protocol
- **HTTP/REST APIs** - Sensor communication
- **WebSocket** - Real-time data streaming

### Frontend Technologies
- **SCSS/CSS3** - Advanced styling capabilities
- **JavaScript ES6+** - Modern client-side scripting
- **Chart.js** - Interactive data visualization
- **Responsive Design** - Multi-device compatibility

### Backend & Data
- **Microflows** - Server-side business logic
- **Nanoflows** - Client-side reactive logic
- **PostgreSQL/SQL Server** - Production databases
- **Local Storage** - Offline data management

### Development Tools
- **Mendix Studio Pro** - Visual development IDE
- **Git Integration** - Version control
- **Team Server** - Collaborative development
- **Mendix Console** - Debugging and monitoring

## 📱 Responsive Design & Platform Support

### Supported Platforms
- **Web Application**: Chrome, Firefox, Safari, Edge
- **Progressive Web App (PWA)**: Offline capabilities
- **Native Mobile**: iOS and Android (React Native)
- **Hybrid Mobile**: Cordova/PhoneGap support

### Screen Adaptations
- **Desktop** (≥1200px): Full dashboard layout
- **Tablet** (768px-1199px): Condensed interface
- **Mobile** (≤767px): Touch-optimized design

## 🔄 Real-Time Data Flow

### Sensor Data Pipeline
1. **WoT Sensors** → HTTP endpoints
2. **Mendix WoT Client** → Data retrieval
3. **Microflows** → Data processing & validation
4. **Database** → Persistent storage
5. **Frontend** → Real-time visualization

### Update Frequency
- **Sensor Data**: Every second
- **Motion Tracking**: 30 FPS during exercises
- **Patient Status**: Real-time during sessions

## 📈 Monitoring & Analytics

### Built-in Analytics
- **Sensor Performance Metrics**: Uptime, accuracy, response time
- **Patient Progress Tracking**: Exercise completion, improvement trends
- **System Usage Statistics**: Active users, session duration, feature usage
- **Error Reporting**: Automatic issue detection and logging

### Custom Dashboards
- **Therapist Dashboard**: Patient overview, session management
- **Admin Dashboard**: System health, user activity, sensor status
- **Patient Portal**: Personal progress, exercise history, appointments

## 🚀 Advanced Features

### AI-Powered Insights
- **Motion Pattern Recognition**: Automatic exercise quality assessment
- **Predictive Analytics**: Patient progress forecasting
- **Anomaly Detection**: Unusual sensor readings or patient behavior

### Integration Capabilities
- **Hospital Information Systems (HIS)**: HL7 FHIR compatibility
- **Electronic Health Records (EHR)**: Patient data synchronization
- **Telehealth Platforms**: Remote consultation integration

**Nordic Data Pi MX Sensor Alert** - Advancing healthcare through IoT innovation and intelligent patient
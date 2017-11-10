import React from 'react';
import {
  StyleSheet,
  View,
  Text,
  Dimensions,
  TouchableOpacity
} from 'react-native';
import MapView from 'react-native-maps';
import flagBlueImg from './assets/flag-blue.png';
import flagPinkImg from './assets/flag-pink.png';

const { width, height } = Dimensions.get('window');

const ASPECT_RATIO = width / height;
const LATITUDE = 37.78825;
const LONGITUDE = -122.4324;
const LATITUDE_DELTA = 0.0922;
const LONGITUDE_DELTA = LATITUDE_DELTA * ASPECT_RATIO;
const SPACE = 0.01;

class MultipleMarkers extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      markers: [],
      markerImage: flagPinkImg
    };
  }

  addMarkers() {
    let markers = [...this.state.markers];
    for (var i = 0; i < 100; i++) {
      var latitudeDelta = Math.random() * LATITUDE_DELTA - LATITUDE_DELTA / 2;
      var longitudeDelta = Math.random() * LONGITUDE_DELTA - LONGITUDE_DELTA / 2;
      markers.push({ coordinate: {
        latitude: LATITUDE + latitudeDelta,
        longitude: LONGITUDE + longitudeDelta
      }});
    }
    this.setState({markers: markers});
  }

  changeMarkerImg() {
    this.setState({
      markerImage: this.state.markerImage == flagPinkImg ? flagBlueImg : flagPinkImg
    });
  }

  render() {
    return (
      <View style={styles.container}>
        <MapView
          provider={this.props.provider}
          style={styles.map}
          initialRegion={{
            latitude: LATITUDE,
            longitude: LONGITUDE,
            latitudeDelta: LATITUDE_DELTA,
            longitudeDelta: LONGITUDE_DELTA,
          }}
        >
          {this.state.markers.map(marker => (
            <MapView.Marker
              coordinate={marker.coordinate}
              image={this.state.markerImage}
            >
              <Text style={styles.marker}>TEST</Text>
            </MapView.Marker>
          ))}
        </MapView>
        <View style={styles.buttonContainer}>
          <TouchableOpacity
            onPress={() => this.addMarkers()}
            style={[styles.bubble, styles.button]}
          >
            <Text style={{ fontSize: 20, fontWeight: 'bold' }}> + </Text>
          </TouchableOpacity>
          <TouchableOpacity
            onPress={() => this.changeMarkerImg()}
            style={[styles.bubble, styles.button]}
          >
            <Text style={{ fontSize: 15, fontWeight: 'bold' }}> change </Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }
}

MultipleMarkers.propTypes = {
  provider: MapView.ProviderPropType,
};

const styles = StyleSheet.create({
  container: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'flex-end',
    alignItems: 'center',
  },
  map: {
    ...StyleSheet.absoluteFillObject,
  },
  marker: {
    marginLeft: 46,
    marginTop: 33,
    fontWeight: 'bold',
  },
  bubble: {
    backgroundColor: 'rgba(255,255,255,0.7)',
    paddingHorizontal: 18,
    paddingVertical: 12,
    borderRadius: 20,
  },
  button: {
    width: 80,
    paddingHorizontal: 12,
    alignItems: 'center',
    marginHorizontal: 10,
  },
  buttonContainer: {
    flexDirection: 'row',
    marginVertical: 20,
    backgroundColor: 'transparent',
  },
});

module.exports = MultipleMarkers;

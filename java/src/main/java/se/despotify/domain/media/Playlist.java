package se.despotify.domain.media;

import se.despotify.domain.Store;
import se.despotify.util.ChecksumCalculator;
import se.despotify.util.Hex;
import se.despotify.util.SpotifyURI;
import se.despotify.util.XMLElement;
import se.despotify.client.protocol.command.ChecksumException;
import se.despotify.exceptions.ProtocolException;

import java.util.Iterator;

public class Playlist extends Media implements Iterable<Track>, Visitable {
	private String      name;
	private String      author;
	private MediaList<Track>   tracks;
	private Long        revision;
	private Long        checksum;
	private Boolean     collaborative;

  public Playlist(){
		super();
	}

  public Playlist(byte[] UUID) {
    super(UUID);
  }

  public Playlist(byte[] UUID, String hexUUID) {
    super(UUID, hexUUID);
  }

  public Playlist(String hexUUID) {
    super(hexUUID);
  }


	public Playlist(byte[] UUID, String name, String author, Boolean collaborative){
    super(UUID);
		this.name          = name;
		this.author        = author;
		this.collaborative = collaborative;
	}

  public Playlist(byte[] UUID, String name, String author, Long revision, Long checksum, Boolean collaborative) {
    super(UUID);
    this.name = name;
    this.author = author;
    this.tracks = null;
    this.revision = revision;
    this.checksum = checksum;
    this.collaborative = collaborative;
  }  

  public long calculateChecksum() {
    ChecksumCalculator calculator = new ChecksumCalculator();
    if (tracks != null) {
      for (Track track : tracks) {
        track.accept(calculator);
      }
    }
    return calculator.getValue();
  }

  public void accept(Visitor visitor) {
    visitor.visit(this);
  }

  @Override
  public String getSpotifyURL() {
    return "spotify:user:"+author+":playlist:" + SpotifyURI.toURI(getUUID());
  }

  @Override
  public String getHttpURL() {
    return "http://open.spotify.com/user/"+author+"/playlist/" + SpotifyURI.toURI(getUUID());
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public int size() {
    return getTracks() != null ? getTracks().size() : 0;
  }

  public MediaList<Track> getTracks() {
    return tracks;
  }

  public void setTracks(MediaList<Track> tracks) {
    this.tracks = tracks;
  }

  public Long getRevision() {
    return revision;
  }

  public void setRevision(Long revision) {
    this.revision = revision;
  }

  public Long getChecksum() {
    return checksum;
  }

  public void setChecksum(Long checksum) {
    this.checksum = checksum;
  }

  public Boolean isCollaborative() {
    return collaborative;
  }

  public void setCollaborative(Boolean collaborative) {
    this.collaborative = collaborative;
  }

  public Iterator<Track> iterator(){
		return this.tracks.iterator();
	}

	public static void fromXMLElement(XMLElement playlistElement, Store store, Playlist playlist) throws ProtocolException {

		/* Get "change" element. */
		XMLElement changeElement = playlistElement.getChild("next-change").getChild("change");
		
		/* Set author. */
		playlist.author = changeElement.getChildText("user");
		
		/* Set name. */
		playlist.name = changeElement.getChild("ops").getChildText("name");
		
		/* Get items (comma separated list). */
    if (changeElement.getChild("ops").hasChild("add")) {
      String items = changeElement.getChild("ops").getChild("add").getChildText("items");

      if (playlist.tracks == null) {
        playlist.tracks = new MediaList<Track>();
      }

      /* Add track items. */
      int position = 0;
      for (String trackData : items.split(",")) {
        trackData = trackData.trim();
        final String trackHexUUID;
        if (trackData.length() != 34) {
          if (SpotifyURI.isHex(trackData)) {
            // not sure why playlist UUID is send sometimes. notice it is lacking UUID prefix byte
            assert trackData.equals(playlist.getHexUUID());
            continue;
          } else {
            throw new RuntimeException(trackData + " is not a valid 32 byte hex UUID!");
          }
        } else if (trackData.length() == 34) {
          trackHexUUID = trackData.substring(0, 32);
          assert "01".equals(trackData.substring(32, 34));
        } else {
          throw new RuntimeException("track UUID was not 16+1 or 16 byte!");
        }
        byte[] trackUUID = Hex.toBytes(trackHexUUID);

        if (playlist.getTracks().get(trackUUID) == null) {
          Track track = store.getTrack(trackHexUUID);
          if (!playlist.getTracks().contains(track)) {
            playlist.tracks.add(track);
          } else {
            if (log.isInfoEnabled()) {
              log.info("track " + track.getHexUUID() + " already in list.");
            }
          }

          // todo remove deleted?
        }
        position++; // perhaps we should use this to syncronize any discrepancy
      }
    }
		
		/* Get "version" element. */
		XMLElement versionElement = playlistElement.getChild("next-change").getChild("version");
		
		/* Split version string into parts. */
		String[] parts = versionElement.getText().split(",", 4);
		
		/* Set values. */

    String[] versionTagValues = versionElement.getText().split(",", 4);

    playlist.setRevision(Long.parseLong(versionTagValues[0]));        
    playlist.setChecksum(Long.parseLong(versionTagValues[2]));
    playlist.collaborative = (Integer.parseInt(parts[3]) == 1);

    if (playlist.getTracks() == null) {
      playlist.setTracks(new MediaList<Track>());
    }
    if (playlist.getTracks().size() != Long.parseLong(versionTagValues[1])) {
      throw new RuntimeException("Size missmatch, playlist = " + playlist.getTracks().size() + ", received = " + versionTagValues[1]);
    }
    if (playlist.calculateChecksum() != playlist.getChecksum()) {
      throw new ChecksumException(playlist.getChecksum(), playlist.calculateChecksum());
    }

	}
	
	public static Playlist fromResult(String name, String author, Result result){
		Playlist playlist = new Playlist();
		
		playlist.name   = name;
		playlist.author = author;
		
		for(Track track : result.getTracks()){
			playlist.tracks.add(track);
		}
		
		return playlist;
	}


  @Override
  public String toString() {
    return "Playlist{" +
        "id='" + getHexUUID() + '\'' +
        ", name='" + name + '\'' +
        ", author='" + author + '\'' +
        ", revision=" + revision +
        ", checksum=" + checksum +
        ", collaborative=" + collaborative +
        ", tracks=" + tracks +
        '}';
  }
}

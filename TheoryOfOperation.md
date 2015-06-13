# Introduction #

The London Transit Commission (LTC) offers a [Webwatch](http://www.ltconline.ca/WebWatch) service on their website which will show the next arrivals for a given bus route, in a given direction, at a given stop. The arrival predictions are generally fairly reliable, however they are not presented in a suitable format for a handset display.

The system appears to be the [WebWatch component of Trapeze ITS](http://www.trapezeits.com/solutions-traveler-information-web-based.php) and other North American bus companies using it have very similar-looking websites (for example, [Madison, Wisconsin Metro Transit](http://webwatch.cityofmadison.com/webwatch/prediction.aspx?mode=d), and [Pace Bus in Chicago](http://gis.pacebus.com/webwatch/prediction.aspx?mode=d)).

LTC Buses uses the [jsoup](http://jsoup.org/) HTML parsing library to "scrape" content from the LTC website and present it in a more convenient form.

# Local Database #

First, it scans all routes, all directions and all stops (separately for weekdays, Saturdays and Sundays, since some routes differ). It builds an internal database recording, for each stop, which buses stop there and which direction of their route they are travelling (for example, east or west). This database is stored internally and refreshed only rarely.

(But there's an Open Data London dump of stop data, you say, why not use that? Well, it's missing newer stops, and it only records which route passes each stop, not the direction.)

At some stops a route travels in both directions and the app always fetches times for both directions. Usually these are end-points of routes, for example, stop 1222 (Natural Science) for route 2 Dundas. A few are due to peculiarities of the route (such as route 1 Kipps Lane/Thompson Road at South Street, which is one-way).

Also, in some cases the stop directions may be confusing when they don't match the route direction. For example, 2 Dundas eastbound stops at stop 2045 (Wharncliffe at Mount Pleasant) southbound, but when travelling westbound it stops at stop 2043 (Wharncliffe at Moir) northbound. The app gets all routing when it scans the website so it doesn't care about the directions, just the relationships. If the LTC get it right, the app should get it right too, the LTC system does the hard bit.

# HTML Parsing #

[jsoup](http://jsoup.org/) is used to scrape content from the LTC website. Previously the app scraped the [Plain Text Arrivals](http://www.ltconline.ca/WebWatch/ada.aspx?mode=d) section. However, occasionally this section is down whilst the rest of the site is up (like the [Live Arrivals](http://www.ltconline.ca/WebWatch/prediction.aspx?mode=a) section). Fortunately, there is a hidden mobile site which stays up, needs less data, and is faster.

## Routes ##

The url `http://www.ltconline.ca/WebWatch/MobileAda.aspx` contains a list of all routes, and contains HTML like this:

```
<div align="Center"><b>Live Arrival Times</b><div align="Left">Please choose a route from the following:<br>
<a href="MobileAda.aspx?r=01">KIPPS LANE/THOMPSON ROAD</a><br>
<a href="MobileAda.aspx?r=02">DUNDAS</a><br>

...etc...
```

jsoup code to parse this is similar to:

```
static final Pattern ROUTE_NUM_PATTERN = Pattern.compile("\\?r=(\\d{1,2})");

Connection conn = Jsoup.connect(ROUTE_URL);
Document doc = conn.get();
Elements routeLinks = doc.select("a[href]");
for (Element routeLink : routeLinks) {
	String name = routeLink.text();
	Attributes attrs = routeLink.attributes();
	String href = attrs.get("href");
	Matcher m = ROUTE_NUM_PATTERN.matcher(href);
	if (m.find()) {
		String number = m.group(1);
                // use the name and number
	}
}
```

## Directions ##

Each route has a link like `http://www.ltconline.ca/WebWatch/MobileAda.aspx?r=01` (route 1 in this case - must be two digits), which contains HTML like:

```
<div align="Center"><b>Live Arrival Times</b><div align="Left">Please choose a direction from the following:<br>
<a href="MobileAda.aspx?r=01&d=2">NORTHBOUND</a><br>
<a href="MobileAda.aspx?r=01&d=3">SOUTHBOUND</a><br>
...etc...
```

Parsing code is very similar.

## Stops ##

Finally, each route direction has a link like `http://www.ltconline.ca/WebWatch/MobileAda.aspx?r=01&d=2` containing HTML like:

```
<div align="Center"><b>Live Arrival Times</b><div align="Left">Please choose your stop from the following:<br>
<a href="MobileAda.aspx?r=01&d=2&s=41">1136 Adelaide NB</a><br>
<a href="MobileAda.aspx?r=01&d=2&s=42">1145 Adelaide NB</a><br>
<a href="MobileAda.aspx?r=01&d=2&s=2307">Adelaide at Huron(Adelaide &amp; Huron)</a><br>
...etc...
```

The app uses these together to generate a database where every route, direction and stop are cross-referenced. Many stops will be found more than once, we just rewrite the record each time we see it assuming it to be the same.

## Stop Locations ##

TODO

## Fetching Predictions ##

Predictions are provided by the website only for a single route/direction/stop combination at a time. The app generates, from the database, a list of route/direction combinations that service that stop then calls the prediction URL for each one, which looks like `http://www.ltconline.ca/WebWatch/MobileAda.aspx?r=01&d=2&s=463` and contains:

```
<div align="Center"><b>Live Arrival Times</b><div align="Left">Next 3 Vehicles Arrive at:<br>
10:29 P.M.   TO Kipps Lane at Briarhill<br>
11:34 P.M.   TO Kipps Lane at Briarhill<br>
12:27 A.M.   TO King Edward at Thompson Only<br>

        <br>
Last updated 10:18:47 PM 10/20/2012<br>
```

The code to extract the times looks like:

```
static final Pattern NO_INFO_PATTERN = Pattern.compile("(?mi)no stop information");
static final Pattern ARRIVAL_PATTERN = Pattern.compile("(?i) *(\\d{1,2}:\\d{2} *[\\.apm]*) +(to .+)");

Connection conn = Jsoup.connect(predictionUrl(route, stopNumber));
conn.timeout(FETCH_TIMEOUT);
Document doc = conn.get();
Elements divs = doc.select("div");
if (divs.size() == 0) {
	throw new ScrapeException("LTC down?");
}
for (Element div: divs) {
	List<TextNode> textNodes = div.textNodes();
	for (TextNode node: textNodes) {
		String text = node.text();
		Matcher noStopMatcher = NO_INFO_PATTERN.matcher(text);
		if (noStopMatcher.find()) {
			throw new ScrapeException("none");
		}
		Matcher arrivalMatcher = ARRIVAL_PATTERN.matcher(text);
		HashMap<String, String> crossingTime;
		while (arrivalMatcher.find()) {
			String textTime = arrivalMatcher.group(1);
			String destination = arrivalMatcher.group(2);
			// parse the time, compute time differences, etc.
		}
	}
}
```

The app sorts by arrival time, reformats the times from absolute ("11:45") to relative ("14 minutes") and presents them in a scrollable ListView, soonest first.

# Problems #

The LTC site used to be quite slow, usually at times of heavy load (like rush hour), however those problems have been resolved and it now responds quickly almost all of the time.

On at least one occasion the LTC dropped the A.M./P.M. indicator from the times. If this happens the app takes an intelligent guess about which time is intended.
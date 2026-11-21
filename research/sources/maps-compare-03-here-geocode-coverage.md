Source: https://docs.here.com/geocoding-and-search/docs/geocode-local-coverage
Title: Geocode local coverage
Fetched: 2026-10-18T15:41:51.039Z

For AI agents: the docs index is at https://docs.here.com/geocoding-and-search/llms.txt. The index for every project on this site is at https://docs.here.com/llms.txt. Append .md to any documentation page URL to get its markdown version.

The table below lists the types of coverage available for the `/geocode`, `/revgeocode`, and `/autocomplete` endpoints, organized by countries.

## Coverage categories   [Skip link to Coverage categories](https://docs.here.com/geocoding-and-search/docs/geocode-local-coverage\#coverage-categories)

The following sections provide additional information on the standard data categories used by the `/geocode`, `/revgeocode`, and `/autocomplete` endpoints.

- Coverage level - The following coverage levels are available by country/area:
  - Full: premium house no., street level – Full coverage to house number level with selected house numbers based on premium Point Adressing and/or Premium Address Ranges for high accuracy geocoding results.
  - Full: house no, street level – Full coverage to house number level based on Base Address Ranges that are interpolated for high accuracy geocoding results.
  - Selected streets, cities, country – Coverage down to street level.
  - Major cities, country – Coverage down to city level.
- Map data classification - The underlying maps are assigned one of the following categories:
  - HERE Map Content – This map category represents map content that is comprehensive, automotive-grade data suitable for navigation, location-based services, advanced route planning and enterprise applications. You'll find HERE Map Content in vehicle navigation systems, the top routing websites and millions of the top brands of GPS-enabled wireless devices around the world. They're also the maps powering geographic information systems, homeland security and they're the foundation for a new generation of location-based services.
  - Intermediate Map Content – This map category represents map content that is a separate class of map products provided to support customers who are interested in introducing LBS and navigation applications for emerging markets. Based on third-party source data deemed to be the highest available level of quality, Intermediate Map Content can be suitable for a range of functions, including navigation, search and display, and display only. However, they do not adhere to the same specifications or required verification as the company's flagship product, HERE Map Content. The Intermediate Map Content offering is an essential tool for customers who require maps in a faster time to market for key regions of the world, and desire the same exceptional service and support they have come to expect from us.
  - Entry Map Content – This map category represents map content that is a separate class of map product sourced from a third party, compiled and edge matched to HERE Map Content. Combining HERE Map Content data with Entry Map Content data enables seamless global or regional map-based solutions. This product is designed primarily for map display and positional location functionality. It has no navigable attributes and offers a limited road network. Entry Map Content has a broad variety of inclusion levels per country but each country will comply to a defined minimum specification. Sub-regions have been defined for ease of communication and product distribution: Africa, Asia, Eastern Europe, Indonesia, Latin America and Oceania.
- Localized address labels - This value indicates whether localized address labels are available.

- Point addresses - Point Addressing provides a point adjusted to the road as a precise address location. This product resolves non-standard addressing issues in countries where non-sequential numbering is used or where address ranges do not exist. For some countries, point address data is not available.

- Postal addressing - Countries with postal code systems that offer very precise or granular levels of detail, often assigning postal codes to individual buildings or a small number of buildings.

- Postal codes - This value indicates whether postal code information is available for the country/area. Note that some countries/area do not have ay postal codes, others may have postal codes, but the data is not available, and others have postal codes that are available.

- Postal Code Points - Represent center points for full postal codes for a collection of addresses (address ranges) and/or postal code areas.


## Countries & regions   [Skip link to Countries & regions](https://docs.here.com/geocoding-and-search/docs/geocode-local-coverage\#countries--regions)

| Country/Area | ISO A3 | ISO A2 | Coverage Level | Map Data Classification | Localized Address Labels | Point Addressing | Postal Addressing | Postal Codes | Postal Code Points |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Afghanistan | AFG | AF | major cities, country | Entry Map Content |  |  |  |  |  |
| Albania | ALB | AL | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Algeria | DZA | DZ | selected streets, cities, country | HERE Map Content |  |  |  |  |  |
| American Samoa | ASM | AS | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Andorra | AND | AD | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Angola | AGO | AO | selected streets, cities, country | HERE Map Content |  |  |  | no codes are used |  |
| Anguilla | AIA | AI | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Antarctica | ATA | AQ | major cities, country | Entry Map Content |  |  |  | no codes are used |  |
| Antigua and Barbuda | ATG | AG | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Argentina | ARG | AR | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Armenia | ARM | AM | selected streets, cities, country | Intermediate Map Content | Y | Y |  |  |  |
| Aruba | ABW | AW | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Australia | AUS | AU | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Austria | AUT | AT | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Azerbaijan | AZE | AZ | full: house no, street level | HERE Map Content | Y | Y |  |  |  |
| Bahamas | BHS | BS | full: premium house no., street level | HERE Map Content | Y | Y |  | no codes are used |  |
| Bahrain | BHR | BH | full: premium house no., street level | HERE Map Content | Y | Y |  |  |  |
| Bangladesh | BGD | BD | selected streets, cities, country | HERE Map Content |  |  |  |  |  |
| Barbados | BRB | BB | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Belarus | BLR | BY | full: premium house no., street level | HERE Map Content | Y | Y |  |  |  |
| Belgium | BEL | BE | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Belize | BLZ | BZ | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Benin | BEN | BJ | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Bermuda | BMU | BM | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Bhutan | BTN | BT | major cities, country | Entry Map Content |  |  |  |  |  |
| Bolivia | BOL | BO | full: house no, street level | HERE Map Content |  | Y |  |  |  |
| Bonaire, Sint Eustatius and Saba | BES | BQ | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Bosnia and Herzegovina | BIH | BA | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Botswana | BWA | BW | full: house no, street level | HERE Map Content | Y |  |  | no codes are used |  |
| Brazil | BRA | BR | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| British Indian Ocean Territory | IOT | IO | major cities, country | Entry Map Content |  |  |  |  |  |
| Brunei Darussalam | BRN | BN | full: house no, street level | HERE Map Content | Y | Y |  | Y |  |
| Bulgaria | BGR | BG | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Burkina Faso | BFA | BF | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Burundi | BDI | BI | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Cambodia | KHM | KH | selected streets, cities, country | HERE Map Content |  | Y |  | Y |  |
| Cameroon | CMR | CM | selected streets, cities, country | HERE Map Content |  |  |  | no codes are used |  |
| Canada | CAN | CA | full: premium house no., street level | HERE Map Content | Y | Y | Y | Y | Y |
| Cape Verde | CPV | CV | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Cayman Islands | CYM | KY | full: house no, street level | HERE Map Content | Y |  |  | Y |  |
| Central African Republic | CAF | CF | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Chad | TCD | TD | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Chile | CHL | CL | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| China | CHN | CN | major cities, country | Entry Map Content |  |  |  |  |  |
| Colombia | COL | CO | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Comoros | COM | KM | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Congo | COG | CG | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Congo, The Democratic Republic of The | COD | CD | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Cook Islands | COK | CK | major cities, country | Entry Map Content |  |  |  | no codes are used |  |
| Costa Rica | CRI | CR | full: house no, street level | HERE Map Content |  |  |  | Y |  |
| Cote D'Ivoire | CIV | CI | selected streets, cities, country | HERE Map Content |  |  |  | no codes are used |  |
| Croatia | HRV | HR | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Cuba | CUB | CU | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Curacao | CUW | CW | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Cyprus | CYP | CY | full: premium house no., street level | HERE Map Content |  | Y |  | Y |  |
| Czech Republic | CZE | CZ | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Denmark | DNK | DK | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Djibouti | DJI | DJ | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Dominica | DMA | DM | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Dominican Republic | DOM | DO | selected streets, cities, country | HERE Map Content |  |  |  | Y |  |
| Ecuador | ECU | EC | full: house no, street level | HERE Map Content |  | Y |  |  |  |
| Egypt | EGY | EG | full: premium house no., street level | HERE Map Content | Y | Y |  |  |  |
| El Salvador | SLV | SV | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Equatorial Guinea | GNQ | GQ | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Eritrea | ERI | ER | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Estonia | EST | EE | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Ethiopia | ETH | ET | selected streets, cities, country | HERE Map Content |  |  |  |  |  |
| Falkland Islands (Malvinas) | FLK | FK | selected streets, cities, country | HERE Map Content |  |  |  |  |  |
| Faroe Islands | FRO | FO | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Fiji | FJI | FJ | selected streets, cities, country | Intermediate Map Content |  | Y |  | no codes are used |  |
| Finland | FIN | FI | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| France | FRA | FR | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| French Guiana | GUF | GF | full: premium house no., street level | HERE Map Content |  | Y |  | Y |  |
| French Polynesia | PYF | PF | selected streets, cities, country | Intermediate Map Content |  | Y |  | Y |  |
| Gabon | GAB | GA | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Gambia | GMB | GM | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Georgia | GEO | GE | selected streets, cities, country | Intermediate Map Content | Y | Y |  |  |  |
| Germany | DEU | DE | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Ghana | GHA | GH | selected streets, cities, country | HERE Map Content |  |  |  | no codes are used |  |
| Gibraltar | GIB | GI | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Greece | GRC | GR | full: house no, street level | HERE Map Content | Y | Y |  | Y |  |
| Greenland | GRL | GL | major cities, country | Entry Map Content |  | Y |  |  |  |
| Grenada | GRD | GD | major cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Guadeloupe | GLP | GP | full: premium house no., street level | HERE Map Content |  | Y |  | Y |  |
| Guam | GUM | GU | selected streets, cities, country | Intermediate Map Content |  | Y |  | Y |  |
| Guatemala | GTM | GT | full: house no, street level | HERE Map Content |  |  |  |  |  |
| Guernsey | GGY | GG | full: house no, street level | HERE Map Content (Included in UK map product) | Y |  |  | Y |  |
| Guinea | GIN | GN | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Guinea-Bissau | GNB | GW | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Guyana | GUY | GY | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Haiti | HTI | HT | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Honduras | HND | HN | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Hong Kong | HKG | HK | full: premium house no., street level | HERE Map Content | Y | Y |  | no codes are used |  |
| Hungary | HUN | HU | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Iceland | ISL | IS | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| India | IND | IN | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Indonesia | IDN | ID | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Iran (Islamic Republic of) | IRN | IR | major cities, country | Entry Map Content |  |  |  |  |  |
| Iraq | IRQ | IQ | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Ireland | IRL | IE | full: premium house no., street level | HERE Map Content | Y | Y | Y | Y | Y |
| Isle of Man | IMN | IM | full: house no, street level | HERE Map Content | Y | Y | Y | Y | Y |
| Israel | ISR | IL | full: premium house no., street level | HERE Map Content |  | Y | Y | Y | Y (not in Reverse geocode) |
| Italy | ITA | IT | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Jamaica | JAM | JM | full: house no, street level | HERE Map Content |  | Y |  | Y |  |
| Japan | JPN | JP | full: premium house no., street level (Japan block based addressing) | 3rd party map, fully integrated, Japanese and English language queries supported | Y | Y |  | Y |  |
| Jersey | JEY | JE | full: house no, street level | HERE Map Content (Included in UK map product) | Y |  |  | Y |  |
| Jordan | JOR | JO | full: premium house no., street level | HERE Map Content | Y | Y |  | no codes are used |  |
| Kazakhstan | KAZ | KZ | full: premium house no., street level | HERE Map Content |  | Y |  | Y |  |
| Kenya | KEN | KE | selected streets, cities, country | HERE Map Content |  |  |  | no codes are used |  |
| Kiribati | KIR | KI | major cities, country | Entry Map Content |  |  |  | no codes are used |  |
| Korea, Democratic People's Republic of | PRK | KP | major cities, country | Entry Map Content |  |  |  | no codes are used |  |
| Korea, Republic of | KOR | KR | major cities, country | Entry Map Content |  |  |  |  |  |
| Kosovo | KOS | XK | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Kuwait | KWT | KW | full: premium house no., street level | HERE Map Content | Y | Y |  |  |  |
| Kyrgyzstan | KGZ | KG | selected streets, cities, country | Intermediate Map Content |  | Y |  |  |  |
| Lao People's Democratic Republic | LAO | LA | major cities, country | Entry Map Content |  |  |  |  |  |
| Latvia | LVA | LV | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Lebanon | LBN | LB | full: house no, street level | HERE Map Content |  |  |  |  |  |
| Lesotho | LSO | LS | selected streets, cities, country | HERE Map Content |  |  |  | Y |  |
| Liberia | LBR | LR | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Libyan Arab Jamahiriya | LBY | LY | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Liechtenstein | LIE | LI | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Lithuania | LTU | LT | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Luxembourg | LUX | LU | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Macau | MAC | MO | full: premium house no., street level | HERE Map Content | Y | Y |  | no codes are used |  |
| Macedonia, The Former Yugoslav Republic of | MKD | MK | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Madagascar | MDG | MG | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Malawi | MWI | MW | selected streets, cities, country | HERE Map Content |  |  |  | no codes are used |  |
| Malaysia | MYS | MY | selected streets, cities, country | HERE Map Content | Y | Y |  | Y | Y |
| Maldives | MDV | MV | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Mali | MLI | ML | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Malta | MLT | MT | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Marshall Islands | MHL | MH | major cities, country | Entry Map Content |  |  |  |  |  |
| Martinique | MTQ | MQ | full: premium house no., street level | HERE Map Content |  | Y |  | Y |  |
| Mauritania | MRT | MR | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Mauritius | MUS | MU | selected streets, cities, country | HERE Map Content |  |  |  |  |  |
| Mayotte | MYT | YT | selected streets, cities, country | HERE Map Content |  | Y |  | Y |  |
| Mexico | MEX | MX | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Micronesia, Federated States of | FSM | FM | major cities, country | Entry Map Content |  |  |  |  |  |
| Moldova, Republic of | MDA | MD | full: house no, street level | HERE Map Content | Y | Y |  |  |  |
| Monaco | MCO | MC | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Mongolia | MNG | MN | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Montenegro | MNE | ME | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Montserrat | MSR | MS | major cities, country | Entry Map Content |  |  |  |  |  |
| Morocco | MAR | MA | full: premium house no., street level | HERE Map Content | Y | Y |  |  |  |
| Mozambique | MOZ | MZ | selected streets, cities, country | HERE Map Content | Y |  |  | Y |  |
| Myanmar | MMR | MM | full: house no, street level | HERE Map Content |  | Y |  |  |  |
| Namibia | NAM | NA | full: house no, street level | HERE Map Content | Y |  |  |  |  |
| Nauru | NRU | NR | major cities, country | Entry Map Content |  |  |  | no codes are used |  |
| Nepal | NPL | NP | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Netherlands | NLD | NL | full: premium house no., street level | HERE Map Content | Y | Y | Y | Y | Y |
| New Caledonia | NCL | NC | selected streets, cities, country | Intermediate Map Content |  | Y |  |  |  |
| New Zealand | NZL | NZ | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Nicaragua | NIC | NI | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Niger | NER | NE | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Nigeria | NGA | NG | full: house no, street level | HERE Map Content | Y | Y |  |  |  |
| Niue | NIU | NU | major cities, country | Entry Map Content |  |  |  | no codes are used |  |
| Norfolk Island | NFK | NF | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Northern Mariana Islands | MNP | MP | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Norway | NOR | NO | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Oman | OMN | OM | full: house no, street level | HERE Map Content | Y |  |  | no codes are used |  |
| Pakistan | PAK | PK | major cities, country | Entry Map Content |  |  |  |  |  |
| Palau | PLW | PW | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Palestinian Territory, Occupied | PSE | PS | full: house no, street level | HERE Map Content |  | Y |  | Y |  |
| Panama | PAN | PA | full: premium house no., street level | HERE Map Content |  | Y |  |  |  |
| Papua New Guinea | PNG | PG | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Paraguay | PRY | PY | full: house no, street level | HERE Map Content |  | Y |  | Y |  |
| Peru | PER | PE | full: premium house no., street level | HERE Map Content |  | Y |  | Y |  |
| Philippines | PHL | PH | full: premium house no., street level | HERE Map Content |  | Y |  |  |  |
| Pitcairn | PCN | PN | major cities, country | Entry Map Content |  |  |  |  |  |
| Poland | POL | PL | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Portugal | PRT | PT | full: premium house no., street level | HERE Map Content | Y | Y | Y | Y | Y (not in Reverse geocode) |
| Puerto Rico | PRI | PR | full: premium house no., street level | HERE Map Content |  | Y |  | Y | Y (not in Reverse geocode) |
| Qatar | QAT | QA | full: premium house no., street level | HERE Map Content | Y | Y |  | no codes are used |  |
| Reunion | REU | RE | full: premium house no., street level | HERE Map Content |  | Y |  | Y |  |
| Romania | ROU | RO | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Russian Federation | RUS | RU | Currently Unsupported | 0 | Y | Y |  | Y |  |
| Rwanda | RWA | RW | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Saint Barthélemy | BLM | BL | full: house no, street level | HERE Map Content |  |  |  | Y |  |
| Saint Helena | SHN | SH | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Saint Kitts and Nevis | KNA | KN | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Saint Lucia | LCA | LC | major cities, country | Entry Map Content |  |  |  | no codes are used |  |
| Saint Martin | MAF | MF | full: premium house no., street level | HERE Map Content |  | Y |  | Y |  |
| Saint Pierre and Miquelon | SPM | PM | major cities, country | Entry Map Content |  |  |  |  |  |
| Saint Vincent and The Grenadines | VCT | VC | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Samoa | WSM | WS | major cities, country | Entry Map Content |  |  |  |  |  |
| San Marino | SMR | SM | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Sao Tome and Principe | STP | ST | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Saudi Arabia | SAU | SA | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Senegal | SEN | SN | selected streets, cities, country | HERE Map Content |  |  |  |  |  |
| Serbia | SRB | RS | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Seychelles | SYC | SC | selected streets, cities, country | HERE Map Content |  |  |  | no codes are used |  |
| Sierra Leone | SLE | SL | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Singapore | SGP | SG | full: premium house no., street level | HERE Map Content | Y | Y | Y | Y | Y |
| Sint Maarten | SXM | SX | major cities, country | HERE Map Content |  |  |  | no codes are used |  |
| Slovakia | SVK | SK | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Slovenia | SVN | SI | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Solomon Islands | SLB | SB | major cities, country | Entry Map Content |  |  |  | no codes are used |  |
| Somalia | SOM | SO | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| South Africa | ZAF | ZA | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| South Sudan | SSD | SS | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Spain | ESP | ES | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Sri Lanka | LKA | LK | selected streets, cities, country | HERE Map Content |  | Y |  | Y |  |
| Sudan | SDN | SD | major cities, country | Entry Map Content |  |  |  |  |  |
| Suriname | SUR | SR | selected streets, cities, country | Intermediate Map Content |  | Y |  | no codes are used |  |
| Svalbard and Jan Mayen Islands | SJM | SJ | full: premium house no., street level | HERE Map Content |  | Y |  |  |  |
| Swaziland | SWZ | SZ | selected streets, cities, country | HERE Map Content | Y |  |  | Y |  |
| Sweden | SWE | SE | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Switzerland | CHE | CH | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Syrian Arab Republic | SYR | SY | major cities, country | Entry Map Content |  |  |  | no codes are used |  |
| Taiwan | TWN | TW | full: premium house no., street level | HERE Map Content | Y | Y | Y | Y | Y (not in Reverse geocode) |
| Tajikistan | TJK | TJ | major cities, country | Entry Map Content |  |  |  |  |  |
| Tanzania, United Republic of | TZA | TZ | selected streets, cities, country | HERE Map Content |  |  |  | no codes are used |  |
| Thailand | THA | TH | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Timor-Leste | TLS | TL | selected streets, cities, country | Intermediate Map Content |  | Y |  | no codes are used |  |
| Togo | TGO | TG | selected streets, cities, country | Intermediate Map Content |  |  |  | no codes are used |  |
| Tokelau | TKL | TK | major cities, country | Entry Map Content |  |  |  | no codes are used |  |
| Tonga | TON | TO | selected streets, cities, country | Intermediate Map Content |  | Y |  | no codes are used |  |
| Trinidad and Tobago | TTO | TT | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Tunisia | TUN | TN | full: premium house no., street level | HERE Map Content |  | Y |  |  |  |
| Turkey | TUR | TR | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| Turkmenistan | TKM | TM | major cities, country | Entry Map Content |  |  |  |  |  |
| Turks and Caicos Islands | TCA | TC | major cities, country | Entry Map Content |  |  |  |  |  |
| Tuvalu | TUV | TV | major cities, country | Entry Map Content |  |  |  | no codes are used |  |
| Uganda | UGA | UG | selected streets, cities, country | HERE Map Content |  |  |  | no codes are used |  |
| Ukraine | UKR | UA | full: premium house no., street level | HERE Map Content | Y | Y |  | Y |  |
| United Arab Emirates | ARE | AE | full: premium house no., street level | HERE Map Content | Y | Y |  | no codes are used |  |
| United Kingdom | GBR | GB | full: premium house no., street level | HERE Map Content | Y | Y | Y | Y | Y |
| United States | USA | US | full: premium house no., street level | HERE Map Content | Y | Y | Y | Y | Y (not in Reverse geocode) |
| Uruguay | URY | UY | full: premium house no., street level | HERE Map Content |  | Y |  | Y |  |
| Uzbekistan | UZB | UZ | selected streets, cities, country | Intermediate Map Content |  | Y |  |  |  |
| Vanuatu | VUT | VU | selected streets, cities, country | Intermediate Map Content |  | Y |  | no codes are used |  |
| Vatican City State (Holy See) | VAT | VA | full: premium house no., street level | HERE Map Content |  | Y |  | Y |  |
| Venezuela | VEN | VE | full: premium house no., street level | HERE Map Content |  | Y |  |  |  |
| Vietnam | VNM | VN | full: premium house no., street level | HERE Map Content |  | Y |  |  |  |
| Virgin Islands (British) | VGB | VG | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Virgin Islands (U.S.) | VIR | VI | full: house no, street level | HERE Map Content | Y |  |  | Y |  |
| Wallis and Futuna Islands | WLF | WF | selected streets, cities, country | Intermediate Map Content |  |  |  |  |  |
| Western Sahara | ESH | EH | selected streets, cities, country | HERE Map Content |  |  |  |  |  |
| Yemen | YEM | YE | major cities, country | Entry Map Content |  |  |  | no codes are used |  |
| Zambia | ZMB | ZM | selected streets, cities, country | HERE Map Content |  |  |  |  |  |
| Zimbabwe | ZWE | ZW | full: house no, street level | HERE Map Content |  |  |  |  |  |

Updated 5 months ago

* * *

Did this page help you?

Yes

No

Dynamic map content

[HERE Destination Weather](https://docs.here.com/here-destination-weather) [Map Attributes API](https://docs.here.com/map-attributes-api) [HERE Traffic API](https://docs.here.com/here-traffic-api) [HERE Real-Time Traffic](https://docs.here.com/here-real-time-traffic) [HERE Traffic Analytics Speed Data](https://docs.here.com/here-traffic-analytics) [HERE EV products](https://docs.here.com/here-ev-products) [HERE Fuel Prices](https://docs.here.com/here-fuel-prices) [HERE Parking](https://docs.here.com/here-parking) [HERE Safety Cameras](https://docs.here.com/here-safety-cameras)

Map data

[Introduction to Mapping Concepts](https://docs.here.com/introduction-to-mapping-c) [GIS Data Suite](https://docs.here.com/gis-data-suite) [Indoor Map](https://docs.here.com/indoor-map-skpy) [Map Content](https://docs.here.com/map-content) [Mapmaking](https://docs.here.com/mapmaking) [Data API](https://docs.here.com/data-api-fmrg) [Platform MOM](https://docs.here.com/platform-mom) [Optimized Client Map](https://docs.here.com/optimized-client-map) [HD Live Map](https://docs.here.com/hd-live-map) [Map Feedback API](https://docs.here.com/map-feedback-api) [Maps XML](https://docs.here.com/maps-xml) [Embedded Editor](https://docs.here.com/embedded-editor) [Phonetic Training Data](https://docs.here.com/phonetic-training-data)

Location services

[HERE Geocoding & Search](https://docs.here.com/geocoding-search) [HERE Routing](https://docs.here.com/here-routing) [HERE Map Rendering](https://docs.here.com/here-map-rendering) [HERE Positioning](https://docs.here.com/positioning) [HERE Tour Planning](https://docs.here.com/here-tour-planning) [HERE WeGo Pro](https://docs.here.com/wego-pro) [HERE Transit](https://docs.here.com/here-transit) [Tracking API](https://docs.here.com/tracking-api) [what3words](https://docs.here.com/what3words-diqg) [Coverage](https://docs.here.com/coverage-kpky)

Development enablers

[HERE Location Reasoning](https://docs.here.com/location-reasoning) [HERE SDK](https://docs.here.com/here-sdk) [Maps API for JavaScript](https://docs.here.com/maps-api-for-js) [Anonymizer](https://docs.here.com/anonymizer) [HERE Style Editor](https://docs.here.com/here-style-editor) [SceneXtract](https://docs.here.com/scenextract)

Platform tools

[Identity and access management](https://docs.here.com/identity-and-access-management) [Cost management](https://docs.here.com/usage-api) [Data SDK](https://docs.here.com/data-sdk) [Workspace](https://docs.here.com/workspace-lvmk)

Policies

[Policies](https://docs.here.com/policies) [Local maps regulatory approval](https://docs.here.com/local-maps-regulatory-approval) [System Status API](https://docs.here.com/system-status-api)

handshake\_circle

It's your data. You can decide.

We use technology such as cookies on HERE websites to provide a better user experience, deliver personalized content and promote relevant offers to you, on both HERE and selected third party websites. To make this happen we’re asking you to consent to the use of this technology now, including placement of cookies by third parties as outlined in our cookie policy. The consent settings can be modified at any time in the website. See our ["Privacy/Cookie Policy"](https://legal.here.com/en-gb/privacy/cookies).

Accept all cookies

Reject non-essential cookies

Change Preferences
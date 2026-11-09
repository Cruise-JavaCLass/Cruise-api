#!/bin/bash
sed -i '/<S extends ExcursionReview> S save(S entity);/d' cruise-activity-service/src/main/java/com/cruise/activity/repository/ExcursionReviewRepository.java
sed -i '/@Override/d' cruise-activity-service/src/main/java/com/cruise/activity/repository/ExcursionReviewRepository.java

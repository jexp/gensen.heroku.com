    var selectedApp;

    $(function () {
        $('#shareAppButton').click(function () {
            submitForm();
        });

        $('#emailAddress').keyup(function (key) {
            if (key.which == 13) {
                submitForm();
            }
        });
        $(".youtube").colorbox({iframe:true, innerWidth:640, innerHeight:480});

        $("input[name=app]").change(function () {
            checkCustomGitUrlVisible();
            checkFormReady();
        });

        $("#customGitUrl").keyup(function () {
            checkFormReady();
        });

        checkCustomGitUrlVisible();
    });

    function checkCustomGitUrlVisible() {
        if (isCustomGitUrl()) {
            $("#customGitUrl").show();
        }
        else {
            $("#customGitUrl").hide();
        }
    }

    function isCustomGitUrl() {
        return $("input[name=app]").val()=="customGitUrl";
    }
    function checkFormReady() {
        if (isCustomGitUrl() && $("#customGitUrl").val() == "") {
            return false;
        }

        var validEmail = validateEmail($("#emailAddress").val());
        $('#shareAppButton').attr("disabled", !validEmail);
        return validEmail;
    }

    function submitForm() {
        if (!checkFormReady()) return;
        var selectedAppName = $("input[name=app]").val();

        if (isCustomGitUrl()) {
            selectedApp = {name:selectedAppName, demoUrl:"", sourceUrl:"", gitUrl:$("#customGitUrl").val(), buildTool:""};
        }
        else {
            selectedApp = {name: $("input[name=app]").val(), gitUrl: $("input[name=giturl]").val()};
        }

        if (($('#emailAddress').val() != "") && (selectedApp.gitUrl != "")) {
            $('#createAppForm').children().attr("disabled", true);
            $('#createAppForm').fadeTo("slow", 0.35);
            $('#progressBar').show();
            $('#creatingApp').show();

            $.ajax("/", {
                type:'POST',
                data:{
                    emailAddress:$('#emailAddress').val(),
                    gitUrl:selectedApp.gitUrl
                },
                success:function (data, textStatus, jqXHR) {

                    if (data.result) {
                        $('#createAppForm').children().attr("disabled", false);
                        $('#createAppForm').hide();
                        $('#progressBar').hide();
                        $('#creatingApp').hide();
                        $('#appReady').show();

                        $('#step1').append('<a href="' + data.result.web_url + '">' + data.result.web_url + '</a>');
                        $('#step8').append('<a href="' + data.result.web_url + '">' + data.result.web_url + '</a>');

                        $('#step4').append('git clone -o heroku ' + data.result.git_url);

                        if (selectedApp.buildTool != "") {
                            $('#step2').append('<h5>c) ' + buildTools[selectedApp.buildTool].installInstructions + '</h5>');

                            $('#step6').append('cd ' + data.result.name + '<br/>');

                            $('#step6').append(buildTools[selectedApp.buildTool].buildInstruction);
                        }
                        else {
                            $('#step6').hide();
                        }
                    }
                    else if (data.error) {
                        reenableForm();

                        $.each(data.error, function (index, value) {
                            $("#errors").append(value + "<br/>");
                        });
                    }
                    else {
                        reenableForm();
                        window.alert("Unknown error occured " + data);
                    }
                },
                error:function (jqXHR, textStatus, errorThrown) {
                    reenableForm();
                    $("#errors").append(jqXHR);
                }

            });
        }
        else {
            reenableForm();
            $("#errors").append("Email address or app not specified properly");
        }
    }

    function reenableForm() {
        $('#createAppForm').children().attr("disabled", false);
        $('#createAppForm').fadeTo("slow", 1);
        $('#progressBar').hide();
        $('#creatingApp').hide();
    }

    function validateEmail(email) {
        var re = /^(([^<>()[\]\\.,;:\s@\"]+(\.[^<>()[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/
        return email.match(re)
    }

    var _gaq = _gaq || [];
    _gaq.push(['_setAccount', 'UA-26859570-1']);
    _gaq.push(['_trackPageview']);

    (function () {
        var ga = document.createElement('script');
        ga.type = 'text/javascript';
        ga.async = true;
        ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
        var s = document.getElementsByTagName('script')[0];
        s.parentNode.insertBefore(ga, s);
    })();

